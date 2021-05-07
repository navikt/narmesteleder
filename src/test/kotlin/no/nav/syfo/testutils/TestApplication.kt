package no.nav.syfo.testutils

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.server.testing.TestApplicationEngine
import no.nav.syfo.Environment
import no.nav.syfo.application.setupAuth
import no.nav.syfo.log
import java.nio.file.Paths
import java.util.UUID

fun TestApplicationEngine.setUpTestApplication() {
    start(true)
    application.install(CallLogging) {
        mdc("Nav-Callid") { call ->
            call.request.queryParameters["Nav-Callid"] ?: UUID.randomUUID().toString()
        }
        mdc("Nav-Consumer-Id") { call ->
            call.request.queryParameters["Nav-Consumer-Id"] ?: "narmesteleder"
        }
    }
    application.install(StatusPages) {
        exception<Throwable> { cause ->
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
    val env = Environment(
        clientId = "narmesteleder",
        cluster = "dev",
        clientSecret = "secret",
        jwkKeysUrl = "keys",
        jwtIssuer = "issuer",
        pdlGraphqlPath = "graphql",
        stsUrl = "http://sts",
        databaseUsername = "",
        databasePassword = "",
        dbHost = "",
        dbPort = "",
        dbName = "",
        stsApiKey = "key",
        pdlApiKey = "key",
        loginserviceIdportenDiscoveryUrl = "url",
        loginserviceIdportenAudience = audience,
        registerBasePath = "http://register",
        aaregApiKey = "key",
        eregApiKey = "key",
        allowedOrigin = "tjenester"
    )

    val path = "src/test/resources/jwkset.json"
    val uri = Paths.get(path).toUri().toURL()
    val jwkProvider = JwkProviderBuilder(uri).build()

    application.setupAuth(jwkProvider, jwkProvider, env, "issuer")
    return env
}
