package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "narmesteleder"),
    override val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    val clientId: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val clientSecret: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
    val jwkKeysUrl: String = getEnvVar("AZURE_OPENID_CONFIG_JWKS_URI"),
    val jwtIssuer: String = getEnvVar("AZURE_OPENID_CONFIG_ISSUER"),
    val databaseUsername: String = getEnvVar("NAIS_DATABASE_USERNAME"),
    val databasePassword: String = getEnvVar("NAIS_DATABASE_PASSWORD"),
    val dbHost: String = getEnvVar("NAIS_DATABASE_HOST"),
    val dbPort: String = getEnvVar("NAIS_DATABASE_PORT"),
    val dbName: String = getEnvVar("NAIS_DATABASE_DATABASE"),
    val pdlApiKey: String = getEnvVar("PDL_API_KEY"),
    val stsApiKey: String? = getEnvVar("STS_API_KEY"),
    val stsUrl: String = getEnvVar("SECURITYTOKENSERVICE_URL"),
    val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH"),
    val nlResponseTopic: String = "teamsykmelding.syfo-narmesteleder",
    val nlRequestTopic: String = "teamsykmelding.syfo-nl-request",
    val narmesteLederLeesahTopic: String = "teamsykmelding.syfo-narmesteleder-leesah",
    val loginserviceIdportenDiscoveryUrl: String = getEnvVar("LOGINSERVICE_IDPORTEN_DISCOVERY_URL"),
    val loginserviceIdportenAudience: List<String> = getEnvVar("LOGINSERVICE_IDPORTEN_AUDIENCE").split(","),
    val registerBasePath: String = getEnvVar("REGISTER_BASE_PATH"),
    val aaregApiKey: String = getEnvVar("AAREG_API_KEY"),
    val eregApiKey: String = getEnvVar("EREG_API_KEY"),
    val allowedOrigin: List<String> = getEnvVar("ALLOWED_ORIGIN").split(","),
    val redisHost: String = getEnvVar("REDIS_HOST", "narmesteleder-redis.teamsykmelding.svc.cluster.local"),
    val redisPort: Int = getEnvVar("REDIS_PORT_NARMESTELEDER", "6379").toInt(),
    val redisSecret: String = getEnvVar("REDIS_PASSWORD"),
    val tokenXWellKnownUrl: String = getEnvVar("TOKEN_X_WELL_KNOWN_URL"),
    val narmestelederTokenXClientId: String = getEnvVar("TOKEN_X_CLIENT_ID"),
    override val truststore: String? = getEnvVar("NAV_TRUSTSTORE_PATH"),
    override val truststorePassword: String? = getEnvVar("NAV_TRUSTSTORE_PASSWORD"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val sendtSykmeldingKafkaTopic: String = "syfo-sendt-sykmelding",
    val narmesteLederIdTopic: String = "teamsykmelding.syfo-nl-id"
) : KafkaConfig {
    fun jdbcUrl(): String {
        return "jdbc:postgresql://$dbHost:$dbPort/$dbName"
    }
}

data class VaultSecrets(
    val serviceuserUsername: String = getEnvVar("SERVICEUSER_USERNAME"),
    val serviceuserPassword: String = getEnvVar("SERVICEUSER_PASSWORD"),
    override val kafkaPassword: String = serviceuserPassword,
    override val kafkaUsername: String = serviceuserUsername
) : KafkaCredentials

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
