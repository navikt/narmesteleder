package no.nav.syfo

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.get
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.db.Database
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.narmesteleder.oppdatering.OppdaterNarmesteLederService
import no.nav.syfo.narmesteleder.oppdatering.kafka.NLResponseProducer
import no.nav.syfo.narmesteleder.oppdatering.kafka.NarmesteLederResponseConsumerService
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.NlResponseKafkaMessage
import no.nav.syfo.narmesteleder.oppdatering.kafka.util.JacksonKafkaDeserializer
import no.nav.syfo.narmesteleder.oppdatering.kafka.util.JacksonKafkaSerializer
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.service.PdlPersonService
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.concurrent.TimeUnit

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.narmesteleder")

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}

@KtorExperimentalAPI
fun main() {
    val env = Environment()
    val vaultSecrets = VaultSecrets()
    val jwkProvider = JwkProviderBuilder(URL(env.jwkKeysUrl))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()
    DefaultExports.initialize()
    val applicationState = ApplicationState()
    val database = Database(env)

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }
    val httpClient = HttpClient(Apache, config)

    val wellKnown = getWellKnown(httpClient, env.loginserviceIdportenDiscoveryUrl)
    val jwkProviderLoginservice = JwkProviderBuilder(URL(wellKnown.jwks_uri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    val stsOidcClient = StsOidcClient(
        username = vaultSecrets.serviceuserUsername,
        password = vaultSecrets.serviceuserPassword,
        stsUrl = env.stsUrl,
        apiKey = env.stsApiKey
    )
    val pdlClient = PdlClient(
        httpClient,
        env.pdlGraphqlPath,
        env.pdlApiKey,
        PdlClient::class.java.getResource("/graphql/getPerson.graphql").readText().replace(Regex("[\n\t]"), "")
    )
    val pdlPersonService = PdlPersonService(pdlClient, stsOidcClient)

    val kafkaConsumer = KafkaConsumer(
        KafkaUtils.getAivenKafkaConfig().toConsumerConfig("narmesteleder", JacksonKafkaDeserializer::class),
        StringDeserializer(),
        JacksonKafkaDeserializer(NlResponseKafkaMessage::class)
    )
    val kafkaProducerNlResponse = KafkaProducer<String, NlResponseKafkaMessage>(
        KafkaUtils
            .getAivenKafkaConfig()
            .toProducerConfig("narmesteleder-producer", JacksonKafkaSerializer::class, StringSerializer::class)
    )
    val nlResponseProducer = NLResponseProducer(kafkaProducerNlResponse, env.nlResponseTopic)

    val applicationEngine = createApplicationEngine(
        env = env,
        applicationState = applicationState,
        jwkProvider = jwkProvider,
        jwkProviderLoginservice = jwkProviderLoginservice,
        loginserviceIssuer = wellKnown.issuer,
        database = database,
        pdlPersonService = pdlPersonService,
        nlResponseProducer = nlResponseProducer
    )

    val oppdaterNarmesteLederService = OppdaterNarmesteLederService(pdlPersonService, database)
    val narmesteLederResponseConsumerService = NarmesteLederResponseConsumerService(
        kafkaConsumer,
        applicationState,
        env.nlResponseTopic,
        oppdaterNarmesteLederService
    )
    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()
    applicationState.ready = true

    startBackgroundJob(applicationState) {
        narmesteLederResponseConsumerService.startConsumer()
    }
}

fun startBackgroundJob(applicationState: ApplicationState, block: suspend CoroutineScope.() -> Unit) {
    GlobalScope.launch {
        try {
            block()
        } catch (ex: Exception) {
            log.error("Error in background task, restarting application")
            applicationState.alive = false
            applicationState.ready = false
        }
    }
}

fun getWellKnown(httpClient: HttpClient, wellKnownUrl: String) =
    runBlocking { httpClient.get<WellKnown>(wellKnownUrl) }

@JsonIgnoreProperties(ignoreUnknown = true)
data class WellKnown(
    val authorization_endpoint: String,
    val token_endpoint: String,
    val jwks_uri: String,
    val issuer: String
)
