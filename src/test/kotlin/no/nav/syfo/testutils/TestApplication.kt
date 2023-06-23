package no.nav.syfo.testutils

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.testing.TestApplicationEngine
import java.nio.file.Paths
import java.util.UUID
import no.nav.syfo.Environment
import no.nav.syfo.application.setupAuth
import no.nav.syfo.log
import org.slf4j.event.Level

fun TestApplicationEngine.setUpTestApplication() {
    start(true)
    application.install(CallLogging) {
        level = Level.TRACE
        mdc("Nav-Callid") { call ->
            call.request.queryParameters["Nav-Callid"] ?: UUID.randomUUID().toString()
        }
        mdc("Nav-Consumer-Id") { call ->
            call.request.queryParameters["Nav-Consumer-Id"] ?: "narmesteleder"
        }
    }
    application.install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
            log.error("Caught exception", cause)
        }
    }
    application.install(ContentNegotiation) {
        jackson {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }
}

val testAudience = listOf("loginserviceId1", "loginserviceId2")

fun TestApplicationEngine.setUpAuth(audience: List<String> = testAudience): Environment {
    val env =
        Environment(
            clientId = "narmesteleder",
            cluster = "dev",
            clientSecret = "secret",
            jwkKeysUrl = "keys",
            jwtIssuer = "issuer",
            pdlGraphqlPath = "graphql",
            databaseUsername = "",
            databasePassword = "",
            dbHost = "",
            dbPort = "",
            dbName = "",
            loginserviceIdportenDiscoveryUrl = "url",
            loginserviceIdportenAudience = audience,
            aaregUrl = "aareg",
            aaregScope = "aareg-scope",
            allowedOrigin = listOf("tjenester", "www"),
            redisSecret = "secret",
            tokenXWellKnownUrl = "https://tokenx",
            narmestelederTokenXClientId = "id",
            pdlScope = "scope",
            aadAccessTokenV2Url = "url",
            clientIdV2 = "id",
            clientSecretV2 = "hush",
            schemaRegistryUrl = "schemaAiven",
            kafkaSchemaRegistryUsername = "user",
            kafkaSchemaRegistryPassword = "pw",
            electorPath = "elector",
        )

    val path = "src/test/resources/jwkset.json"
    val uri = Paths.get(path).toUri().toURL()
    val jwkProvider = JwkProviderBuilder(uri).build()

    application.setupAuth(jwkProvider, jwkProvider, jwkProvider, env, "issuer", "issuer")
    return env
}
