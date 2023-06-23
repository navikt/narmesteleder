package no.nav.syfo.application

import com.auth0.jwk.JwkProvider
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.Principal
import io.ktor.server.auth.jwt.JWTCredential
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.request.header
import io.ktor.server.request.path
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.Environment
import no.nav.syfo.application.metrics.APP_ID_PATH_COUNTER
import no.nav.syfo.application.metrics.getLabel
import no.nav.syfo.log

fun Application.setupAuth(
    jwkProvider: JwkProvider,
    jwkProviderLoginservice: JwkProvider,
    jwkProviderTokenX: JwkProvider,
    env: Environment,
    loginserviceIssuer: String,
    tokenXIssuer: String,
) {
    install(Authentication) {
        jwt("servicebruker") {
            verifier(jwkProvider, env.jwtIssuer)
            realm = "Narmesteleder"
            validate { credentials ->
                when {
                    harTilgang(credentials, env.clientId) -> {
                        val appid: String = credentials.payload.getClaim("azp").asString()
                        val app = env.preAuthorizedApp.firstOrNull() { it.clientId == appid }
                        if (app != null) {
                            APP_ID_PATH_COUNTER.labels(
                                    app.team,
                                    app.appName,
                                    getLabel(this.request.path())
                                )
                                .inc()
                        } else {
                            log.warn("App not in pre authorized list: $appid")
                        }
                        JWTPrincipal(credentials.payload)
                    }
                    else -> unauthorized(credentials)
                }
            }
        }
        jwt(name = "loginservice") {
            authHeader {
                if (it.getToken() == null) {
                    return@authHeader null
                }
                return@authHeader HttpAuthHeader.Single("Bearer", it.getToken()!!)
            }
            verifier(jwkProviderLoginservice, loginserviceIssuer)
            validate { credentials ->
                when {
                    hasLoginserviceIdportenClientIdAudience(
                        credentials,
                        env.loginserviceIdportenAudience
                    ) && erNiva4(credentials) -> {
                        val principal = JWTPrincipal(credentials.payload)
                        BrukerPrincipal(
                            fnr = finnFnrFraToken(principal),
                            principal = principal,
                        )
                    }
                    else -> unauthorized(credentials)
                }
            }
        }
        jwt(name = "tokenx") {
            authHeader {
                if (it.getToken() == null) {
                    return@authHeader null
                }
                return@authHeader HttpAuthHeader.Single("Bearer", it.getToken()!!)
            }
            verifier(jwkProviderTokenX, tokenXIssuer)
            validate { credentials ->
                when {
                    harNarmestelederAudience(credentials, env.narmestelederTokenXClientId) &&
                        erNiva4(credentials) -> {
                        val principal = JWTPrincipal(credentials.payload)
                        BrukerPrincipal(
                            fnr = finnFnrFraToken(principal),
                            principal = principal,
                        )
                    }
                    else -> unauthorized(credentials)
                }
            }
        }
    }
}

fun ApplicationCall.getToken(): String? {
    if (request.header("Authorization") != null) {
        return request.header("Authorization")!!.removePrefix("Bearer ")
    }
    return request.cookies.get(name = "selvbetjening-idtoken")
}

fun finnFnrFraToken(principal: JWTPrincipal): String {
    return if (
        principal.payload.getClaim("pid") != null &&
            !principal.payload.getClaim("pid").asString().isNullOrEmpty()
    ) {
        log.debug("Bruker fnr fra pid-claim")
        principal.payload.getClaim("pid").asString()
    } else {
        log.debug("Bruker fnr fra subject")
        principal.payload.subject
    }
}

fun harTilgang(credentials: JWTCredential, clientId: String): Boolean {
    val appid: String = credentials.payload.getClaim("azp").asString()
    log.debug("authorization attempt for $appid")
    return credentials.payload.audience.contains(clientId)
}

fun unauthorized(credentials: JWTCredential): Principal? {
    log.warn(
        "Auth: Unexpected audience for jwt {}, {}",
        StructuredArguments.keyValue("issuer", credentials.payload.issuer),
        StructuredArguments.keyValue("audience", credentials.payload.audience),
    )
    return null
}

fun hasLoginserviceIdportenClientIdAudience(
    credentials: JWTCredential,
    loginserviceIdportenClientId: List<String>
): Boolean {
    return loginserviceIdportenClientId.any { credentials.payload.audience.contains(it) }
}

fun harNarmestelederAudience(credentials: JWTCredential, clientId: String): Boolean {
    return credentials.payload.audience.contains(clientId)
}

fun erNiva4(credentials: JWTCredential): Boolean {
    return "Level4" == credentials.payload.getClaim("acr").asString()
}

data class BrukerPrincipal(
    val fnr: String,
    val principal: JWTPrincipal,
) : Principal
