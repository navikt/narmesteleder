package no.nav.syfo

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.features.HttpResponseValidator
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.get
import io.ktor.network.sockets.SocketTimeoutException
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.client.AccessTokenClientV2
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.db.Database
import no.nav.syfo.application.exception.ServiceUnavailableException
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.identendring.IdentendringService
import no.nav.syfo.identendring.PdlAktorConsumer
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.narmesteleder.arbeidsforhold.client.ArbeidsforholdClient
import no.nav.syfo.narmesteleder.arbeidsforhold.service.ArbeidsgiverService
import no.nav.syfo.narmesteleder.oppdatering.OppdaterNarmesteLederService
import no.nav.syfo.narmesteleder.oppdatering.kafka.NLRequestProducer
import no.nav.syfo.narmesteleder.oppdatering.kafka.NLResponseProducer
import no.nav.syfo.narmesteleder.oppdatering.kafka.NarmesteLederLeesahProducer
import no.nav.syfo.narmesteleder.oppdatering.kafka.NarmesteLederResponseConsumerService
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.NarmesteLederLeesah
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.NlRequestKafkaMessage
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.NlResponseKafkaMessage
import no.nav.syfo.narmesteleder.oppdatering.kafka.util.JacksonKafkaDeserializer
import no.nav.syfo.narmesteleder.oppdatering.kafka.util.JacksonKafkaSerializer
import no.nav.syfo.narmesteleder.organisasjon.client.OrganisasjonsinfoClient
import no.nav.syfo.narmesteleder.organisasjon.redis.OrganisasjonsinfoRedisService
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.redis.PdlPersonRedisService
import no.nav.syfo.pdl.service.PdlPersonService
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.time.ExperimentalTime

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.narmesteleder")

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}

@ExperimentalTime
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

    val jedisPool = JedisPool(JedisPoolConfig(), env.redisHost, env.redisPort)

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        HttpResponseValidator {
            handleResponseException { exception ->
                when (exception) {
                    is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
                }
            }
        }
    }
    val httpClient = HttpClient(Apache, config)

    val accessTokenClientV2 = AccessTokenClientV2(env.aadAccessTokenV2Url, env.clientIdV2, env.clientSecretV2, httpClient)

    val wellKnown = getWellKnown(httpClient, env.loginserviceIdportenDiscoveryUrl)
    val jwkProviderLoginservice = JwkProviderBuilder(URL(wellKnown.jwks_uri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    val wellKnownTokenX = getWellKnownTokenX(httpClient, env.tokenXWellKnownUrl)
    val jwkProviderTokenX = JwkProviderBuilder(URL(wellKnownTokenX.jwks_uri))
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
    val pdlPersonRedisService = PdlPersonRedisService(jedisPool, env.redisSecret)
    val pdlPersonService = PdlPersonService(pdlClient, accessTokenClientV2, pdlPersonRedisService, env.pdlScope)
    val arbeidsforholdClient = ArbeidsforholdClient(httpClient, env.registerBasePath, env.aaregApiKey)
    val arbeidsgiverService = ArbeidsgiverService(arbeidsforholdClient, stsOidcClient)
    val organisasjonsinfoRedisService = OrganisasjonsinfoRedisService(jedisPool, env.redisSecret)
    val organisasjonsinfoClient = OrganisasjonsinfoClient(httpClient, env.registerBasePath, env.eregApiKey, organisasjonsinfoRedisService)

    val kafkaConsumer = KafkaConsumer(
        KafkaUtils.getAivenKafkaConfig().also { it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none" }.toConsumerConfig("narmesteleder-v2", JacksonKafkaDeserializer::class),
        StringDeserializer(),
        JacksonKafkaDeserializer(NlResponseKafkaMessage::class)
    )
    val kafkaProducerNlResponse = KafkaProducer<String, NlResponseKafkaMessage>(
        KafkaUtils
            .getAivenKafkaConfig()
            .toProducerConfig("${env.applicationName}-producer", JacksonKafkaSerializer::class, StringSerializer::class)
    )
    val nlResponseProducer = NLResponseProducer(kafkaProducerNlResponse, env.nlResponseTopic)
    val kafkaProducerNlRequest = KafkaProducer<String, NlRequestKafkaMessage>(
        KafkaUtils
            .getAivenKafkaConfig()
            .toProducerConfig("${env.applicationName}-producer", JacksonKafkaSerializer::class, StringSerializer::class)
    )
    val nlRequestProducer = NLRequestProducer(kafkaProducerNlRequest, env.nlRequestTopic)
    val kafkaProducerNarmesteLederLeesah = KafkaProducer<String, NarmesteLederLeesah>(
        KafkaUtils
            .getAivenKafkaConfig()
            .toProducerConfig("${env.applicationName}-producer", JacksonKafkaSerializer::class, StringSerializer::class)
    )
    val kafkaBaseConfig = loadBaseConfig(env, vaultSecrets).envOverrides()
    kafkaBaseConfig["auto.offset.reset"] = "latest"
    kafkaBaseConfig["specific.avro.reader"] = false
    kafkaBaseConfig["schema.registry.url"] = env.avroSchemaRegistryUrl

    val kafkaConsumerPdlAktor = KafkaConsumer<String, GenericRecord>(
        kafkaBaseConfig.toConsumerConfig("${env.applicationName}-consumer", valueDeserializer = KafkaAvroDeserializer::class).also {
            it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"
        }
    )
    val narmesteLederLeesahProducer = NarmesteLederLeesahProducer(kafkaProducerNarmesteLederLeesah, env.narmesteLederLeesahTopic)

    val applicationEngine = createApplicationEngine(
        env = env,
        applicationState = applicationState,
        jwkProvider = jwkProvider,
        jwkProviderLoginservice = jwkProviderLoginservice,
        jwkProviderTokenX = jwkProviderTokenX,
        loginserviceIssuer = wellKnown.issuer,
        tokenXIssuer = wellKnownTokenX.issuer,
        database = database,
        pdlPersonService = pdlPersonService,
        nlResponseProducer = nlResponseProducer,
        nlRequestProducer = nlRequestProducer,
        arbeidsgiverService = arbeidsgiverService,
        organisasjonsinfoClient = organisasjonsinfoClient
    )

    val oppdaterNarmesteLederService = OppdaterNarmesteLederService(pdlPersonService, database, narmesteLederLeesahProducer)
    val narmesteLederResponseConsumerService = NarmesteLederResponseConsumerService(
        kafkaConsumer,
        applicationState,
        env.nlResponseTopic,
        oppdaterNarmesteLederService,
        env.cluster
    )

    val identendringService = IdentendringService(database, oppdaterNarmesteLederService)
    val pdlAktorConsumer = PdlAktorConsumer(kafkaConsumerPdlAktor, applicationState, env.pdlAktorTopic, identendringService)

    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()
    applicationState.ready = true

    narmesteLederResponseConsumerService.startConsumer()
    pdlAktorConsumer.startConsumer()
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

fun getWellKnownTokenX(httpClient: HttpClient, wellKnownUrl: String) =
    runBlocking { httpClient.get<WellKnownTokenX>(wellKnownUrl) }

@JsonIgnoreProperties(ignoreUnknown = true)
data class WellKnownTokenX(
    val token_endpoint: String,
    val jwks_uri: String,
    val issuer: String
)
