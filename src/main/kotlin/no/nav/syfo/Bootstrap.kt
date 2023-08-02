package no.nav.syfo

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.serialization.jackson.jackson
import io.prometheus.client.hotspot.DefaultExports
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.runBlocking
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.client.AccessTokenClientV2
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.db.Database
import no.nav.syfo.application.exception.ServiceUnavailableException
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.kafka.aiven.KafkaUtils
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
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.identendring.IdentendringService
import no.nav.syfo.pdl.identendring.PdlAktorConsumer
import no.nav.syfo.pdl.kafka.PdlLeesahConsumer
import no.nav.syfo.pdl.redis.PdlPersonRedisService
import no.nav.syfo.pdl.service.PdlPersonService
import org.apache.avro.specific.SpecificRecord
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.narmesteleder")

val securelog = LoggerFactory.getLogger("securelog")

val objectMapper: ObjectMapper =
    ObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    }

@DelicateCoroutinesApi
@ExperimentalTime
fun main() {
    val env = Environment()
    val jwkProvider =
        JwkProviderBuilder(URL(env.jwkKeysUrl))
            .cached(10, 24, TimeUnit.HOURS)
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()
    DefaultExports.initialize()
    val applicationState = ApplicationState()
    val database = Database(env)

    val jedisPool = JedisPool(JedisPoolConfig(), env.redisHost, env.redisPort)

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        HttpResponseValidator {
            handleResponseExceptionWithRequest { exception, _ ->
                when (exception) {
                    is SocketTimeoutException ->
                        throw ServiceUnavailableException(exception.message)
                }
            }
        }
        install(HttpRequestRetry) {
            constantDelay(50, 0, false)
            retryOnExceptionIf(3) { request, throwable ->
                log.warn("Caught exception ${throwable.message}, for url ${request.url}")
                true
            }
            retryIf(maxRetries) { request, response ->
                if (response.status.value.let { it in 500..599 }) {
                    log.warn(
                        "Retrying for statuscode ${response.status.value}, for url ${request.url}"
                    )
                    true
                } else {
                    false
                }
            }
        }
        install(HttpTimeout) {
            socketTimeoutMillis = 40_000
            connectTimeoutMillis = 40_000
            requestTimeoutMillis = 40_000
        }
    }
    val httpClient = HttpClient(Apache, config)

    val accessTokenClientV2 =
        AccessTokenClientV2(env.aadAccessTokenV2Url, env.clientIdV2, env.clientSecretV2, httpClient)

    val wellKnown = getWellKnown(httpClient, env.loginserviceIdportenDiscoveryUrl)
    val jwkProviderLoginservice =
        JwkProviderBuilder(URL(wellKnown.jwks_uri))
            .cached(10, 24, TimeUnit.HOURS)
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()

    val wellKnownTokenX = getWellKnownTokenX(httpClient, env.tokenXWellKnownUrl)
    val jwkProviderTokenX =
        JwkProviderBuilder(URL(wellKnownTokenX.jwks_uri))
            .cached(10, 24, TimeUnit.HOURS)
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()

    val pdlClient =
        PdlClient(
            httpClient,
            env.pdlGraphqlPath,
            PdlClient::class
                .java
                .getResource("/graphql/getPerson.graphql")!!
                .readText()
                .replace(Regex("[\n\t]"), ""),
        )
    val pdlPersonRedisService = PdlPersonRedisService(jedisPool, env.redisSecret)
    val pdlPersonService =
        PdlPersonService(pdlClient, accessTokenClientV2, pdlPersonRedisService, env.pdlScope)
    val arbeidsforholdClient = ArbeidsforholdClient(httpClient, env.aaregUrl)
    val arbeidsgiverService =
        ArbeidsgiverService(arbeidsforholdClient, accessTokenClientV2, env.aaregScope)

    val kafkaConsumer =
        KafkaConsumer(
            KafkaUtils.getAivenKafkaConfig("nl-response-consumer")
                .also { it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none" }
                .toConsumerConfig("narmesteleder-v2", JacksonKafkaDeserializer::class),
            StringDeserializer(),
            JacksonKafkaDeserializer(NlResponseKafkaMessage::class),
        )
    val kafkaProducerNlResponse =
        KafkaProducer<String, NlResponseKafkaMessage>(
            KafkaUtils.getAivenKafkaConfig("nl-response-producer")
                .toProducerConfig(
                    "${env.applicationName}-producer",
                    JacksonKafkaSerializer::class,
                    StringSerializer::class
                ),
        )
    val nlResponseProducer = NLResponseProducer(kafkaProducerNlResponse, env.nlResponseTopic)
    val kafkaProducerNlRequest =
        KafkaProducer<String, NlRequestKafkaMessage>(
            KafkaUtils.getAivenKafkaConfig("nl-request-producer")
                .toProducerConfig(
                    "${env.applicationName}-producer",
                    JacksonKafkaSerializer::class,
                    StringSerializer::class
                ),
        )
    val nlRequestProducer = NLRequestProducer(kafkaProducerNlRequest, env.nlRequestTopic)
    val kafkaProducerNarmesteLederLeesah =
        KafkaProducer<String, NarmesteLederLeesah>(
            KafkaUtils.getAivenKafkaConfig("nl-leesah-producer")
                .toProducerConfig(
                    "${env.applicationName}-producer",
                    JacksonKafkaSerializer::class,
                    StringSerializer::class
                ),
        )
    val narmesteLederLeesahProducer =
        NarmesteLederLeesahProducer(kafkaProducerNarmesteLederLeesah, env.narmesteLederLeesahTopic)

    val applicationEngine =
        createApplicationEngine(
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
        )

    val oppdaterNarmesteLederService =
        OppdaterNarmesteLederService(
            pdlPersonService,
            arbeidsgiverService,
            database,
            narmesteLederLeesahProducer,
            nlRequestProducer
        )
    val narmesteLederResponseConsumerService =
        NarmesteLederResponseConsumerService(
            kafkaConsumer,
            applicationState,
            env.nlResponseTopic,
            oppdaterNarmesteLederService,
            env.cluster,
        )

    val identendringService =
        IdentendringService(database, oppdaterNarmesteLederService, pdlPersonService)
    val leaderElection = LeaderElection(httpClient, env.electorPath)
    val pdlAktorConsumer =
        PdlAktorConsumer(
            getKafkaConsumerAivenPdl("pdl-ident-consumer", env),
            applicationState,
            env.pdlAktorV2Topic,
            leaderElection,
            identendringService
        )

    val personhendelseConsumer =
        getKafkaConsumerAivenPdl<Personhendelse>("pdl-leesah-consumer", env)
    val pdlLeesahConsumer =
        PdlLeesahConsumer(personhendelseConsumer, applicationState, env.pdlLeesahTopic, database)
    pdlLeesahConsumer.start()
    val applicationServer = ApplicationServer(applicationEngine, applicationState)

    narmesteLederResponseConsumerService.startConsumer()
    pdlAktorConsumer.startConsumer()

    applicationServer.start()
}

private fun getPdlLeesahConsumer(environment: Environment): KafkaConsumer<String, Personhendelse> {
    val consumerProperties =
        KafkaUtils.getAivenKafkaConfig("pdl-leesah-consumer")
            .apply {
                setProperty(
                    KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG,
                    environment.schemaRegistryUrl
                )
                setProperty(
                    KafkaAvroSerializerConfig.USER_INFO_CONFIG,
                    "${environment.kafkaSchemaRegistryUsername}:${environment.kafkaSchemaRegistryPassword}"
                )
                setProperty(KafkaAvroSerializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE, "USER_INFO")
            }
            .toConsumerConfig(
                "narmesteleder-consumer",
                valueDeserializer = KafkaAvroDeserializer::class,
            )
            .also {
                it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
                it["specific.avro.reader"] = true
            }
    return KafkaConsumer<String, Personhendelse>(consumerProperties)
}

fun <T : SpecificRecord> getKafkaConsumerAivenPdl(
    clientId: String,
    environment: Environment
): KafkaConsumer<String, T> {
    val consumerProperties =
        KafkaUtils.getAivenKafkaConfig(clientId)
            .apply {
                setProperty(
                    KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG,
                    environment.schemaRegistryUrl
                )
                setProperty(
                    KafkaAvroSerializerConfig.USER_INFO_CONFIG,
                    "${environment.kafkaSchemaRegistryUsername}:${environment.kafkaSchemaRegistryPassword}"
                )
                setProperty(KafkaAvroSerializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE, "USER_INFO")
            }
            .toConsumerConfig(
                "${environment.applicationName}-consumer",
                valueDeserializer = KafkaAvroDeserializer::class,
            )
            .also {
                it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
                it["specific.avro.reader"] = true
                it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"
            }

    return KafkaConsumer<String, T>(consumerProperties)
}

fun getWellKnown(httpClient: HttpClient, wellKnownUrl: String) = runBlocking {
    httpClient.get(wellKnownUrl).body<WellKnown>()
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class WellKnown(
    val authorization_endpoint: String,
    val token_endpoint: String,
    val jwks_uri: String,
    val issuer: String,
)

fun getWellKnownTokenX(httpClient: HttpClient, wellKnownUrl: String) = runBlocking {
    httpClient.get(wellKnownUrl).body<WellKnownTokenX>()
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class WellKnownTokenX(
    val token_endpoint: String,
    val jwks_uri: String,
    val issuer: String,
)
