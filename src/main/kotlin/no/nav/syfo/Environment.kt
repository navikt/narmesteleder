package no.nav.syfo

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "narmesteleder"),
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
    val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH")
) {
    fun jdbcUrl(): String {
        return "jdbc:postgresql://$dbHost:$dbPort/$dbName"
    }
}

data class VaultSecrets(
    val serviceuserUsername: String = getEnvVar("SERVICEUSER_USERNAME"),
    val serviceuserPassword: String = getEnvVar("SERVICEUSER_PASSWORD")
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
