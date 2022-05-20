package no.nav.syfo.narmesteleder.user

import io.ktor.auth.authenticate
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.mockk.mockk
import kotlinx.coroutines.DelicateCoroutinesApi
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.narmesteleder.NarmesteLederService
import no.nav.syfo.narmesteleder.oppdatering.DeaktiverNarmesteLederService
import no.nav.syfo.narmesteleder.oppdatering.kafka.NLResponseProducer
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.testutils.generateJWTLoginservice
import no.nav.syfo.testutils.setUpAuth
import no.nav.syfo.testutils.setUpTestApplication
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@DelicateCoroutinesApi
class NarmesteLederUserApiKtTest : Spek({
    val nlResponseProducer = mockk<NLResponseProducer>(relaxed = true)
    val pdlPersonService = mockk<PdlPersonService>()
    val database = mockk<DatabaseInterface>()
    val deaktiverNarmesteLederService = DeaktiverNarmesteLederService(
        nlResponseProducer
    )
    val utvidetNarmesteLederService = NarmesteLederService(database, pdlPersonService)

    describe("API for å deaktivere den sykmeldtes nærmeste leder - autentisering") {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            setUpAuth()
            application.routing {
                authenticate("loginservice") {
                    registrerNarmesteLederUserApi(deaktiverNarmesteLederService, utvidetNarmesteLederService)
                }
            }
            it("Aksepterer Authorization-header") {
                with(
                    handleRequest(HttpMethod.Post, "/9999/avkreft") {
                        addHeader("Authorization", "Bearer ${generateJWTLoginservice(audience = "loginserviceId1", subject = "12345678901", issuer = "issuer")}")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                }
            }
            it("Aksepterer ikke Authorization-header med feil issuer") {
                with(
                    handleRequest(HttpMethod.Post, "/9999/avkreft") {
                        addHeader("Authorization", "Bearer ${generateJWTLoginservice(audience = "loginserviceId1", subject = "12345678901", issuer = "annenIssuer")}")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }
            it("Aksepterer ikke Authorization-header med feil audience") {
                with(
                    handleRequest(HttpMethod.Post, "/9999/avkreft") {
                        addHeader("Authorization", "Bearer ${generateJWTLoginservice(audience = "feil", subject = "12345678901", issuer = "issuer")}")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }
            it("Aksepterer token fra cookie") {
                with(
                    handleRequest(HttpMethod.Post, "/9999/avkreft") {
                        addHeader("Cookie", "selvbetjening-idtoken=${generateJWTLoginservice(audience = "loginserviceId1", subject = "12345678901", issuer = "issuer")}")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                }
            }
            it("Aksepterer ikke token med feil issuer fra cookie") {
                with(
                    handleRequest(HttpMethod.Post, "/9999/avkreft") {
                        addHeader("Cookie", "selvbetjening-idtoken=${generateJWTLoginservice(audience = "loginserviceId1", subject = "12345678901", issuer = "annenIssuer")}")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }
            it("Aksepterer ikke token med feil audience fra cookie") {
                with(
                    handleRequest(HttpMethod.Post, "/9999/avkreft") {
                        addHeader("Cookie", "selvbetjening-idtoken=${generateJWTLoginservice(audience = "feil", subject = "12345678901", issuer = "issuer")}")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }
        }
    }
})
