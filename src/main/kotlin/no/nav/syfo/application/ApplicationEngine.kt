package no.nav.syfo.application

import com.auth0.jwk.JwkProvider
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import java.util.UUID
import kotlinx.coroutines.DelicateCoroutinesApi
import no.nav.syfo.Environment
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.application.db.Database
import no.nav.syfo.application.metrics.monitorHttpRequests
import no.nav.syfo.forskuttering.registrerForskutteringApi
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.NarmesteLederService
import no.nav.syfo.narmesteleder.oppdatering.DeaktiverNarmesteLederService
import no.nav.syfo.narmesteleder.oppdatering.kafka.NLResponseProducer
import no.nav.syfo.narmesteleder.registrerNarmesteLederApi
import no.nav.syfo.narmesteleder.user.registrerNarmesteLederUserApi
import no.nav.syfo.narmesteleder.user.registrerNarmesteLederUserApiV2
import org.slf4j.event.Level

@DelicateCoroutinesApi
fun createApplicationEngine(
    env: Environment,
    applicationState: ApplicationState,
    jwkProvider: JwkProvider,
    jwkProviderTokenX: JwkProvider,
    tokenXIssuer: String,
    database: Database,
    nlResponseProducer: NLResponseProducer,
): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> =
    embeddedServer(Netty, env.applicationPort) {
        setUp(
            jwkProvider,
            jwkProviderTokenX,
            env,
            tokenXIssuer,
            database,
            nlResponseProducer,
            applicationState
        )
    }

@DelicateCoroutinesApi
private fun Application.setUp(
    jwkProvider: JwkProvider,
    jwkProviderTokenX: JwkProvider,
    env: Environment,
    tokenXIssuer: String,
    database: Database,
    nlResponseProducer: NLResponseProducer,
    applicationState: ApplicationState
) {
    install(ContentNegotiation) {
        jackson {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }
    setupAuth(
        jwkProvider = jwkProvider,
        jwkProviderTokenX = jwkProviderTokenX,
        env = env,
        tokenXIssuer = tokenXIssuer,
    )
    install(CallId) {
        retrieve { it.request.queryParameters["Nav-Callid"] }
        retrieveFromHeader("Nav-Callid")
        generate { UUID.randomUUID().toString() }
    }
    install(CallLogging) {
        level = Level.TRACE
        mdc("Nav-Callid") { it.callId }
        mdc("Nav-Consumer-Id") { call ->
            call.request.queryParameters["Nav-Consumer-Id"] ?: "narmesteleder"
        }
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            log.error("Caught exception", cause)
            call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
        }
    }
    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
        env.allowedOrigin.forEach { hosts.add("https://$it") }
        allowHeader("nav_csrf_protection")
        allowHeader("Sykmeldt-Fnr")
        allowCredentials = true
        allowNonSimpleContentTypes = true
    }

    val narmesteLederService = NarmesteLederService(database)
    val deaktiverNarmesteLederService = DeaktiverNarmesteLederService(nlResponseProducer)
    routing {
        registerNaisApi(applicationState)
        if (env.cluster == "dev-gcp") {
            swaggerUI(path = "docs", swaggerFile = "openapi/narmesteleder-api.yaml")
        }
        authenticate("servicebruker") {
            registrerForskutteringApi(database)
            registrerNarmesteLederApi(database, narmesteLederService)
        }
        authenticate("tokenx") {
            registrerNarmesteLederUserApiV2(deaktiverNarmesteLederService, narmesteLederService)
            registrerNarmesteLederUserApi(deaktiverNarmesteLederService, narmesteLederService)
        }
    }
    intercept(ApplicationCallPipeline.Monitoring, monitorHttpRequests())
}
