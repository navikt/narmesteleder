package no.nav.syfo.narmesteleder.user

import io.ktor.auth.authenticate
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.util.KtorExperimentalAPI
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.narmesteleder.arbeidsforhold.service.ArbeidsgiverService
import no.nav.syfo.narmesteleder.oppdatering.DeaktiverNarmesteLederService
import no.nav.syfo.narmesteleder.oppdatering.kafka.NLRequestProducer
import no.nav.syfo.narmesteleder.oppdatering.kafka.NLResponseProducer
import no.nav.syfo.narmesteleder.syfonarmesteleder.client.SyfonarmestelederClient
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.testutils.generateJWTLoginservice
import no.nav.syfo.testutils.setUpAuth
import no.nav.syfo.testutils.setUpTestApplication
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
class NarmesteLederUserApiKtTest : Spek({
    val nlResponseProducer = mockk<NLResponseProducer>(relaxed = true)
    val nlRequestProducer = mockk<NLRequestProducer>(relaxed = true)
    val arbeidsgiverService = mockk<ArbeidsgiverService>()
    val pdlPersonService = mockk<PdlPersonService>()
    val syfonarmestelederClient = mockk<SyfonarmestelederClient>()
    val database = mockk<DatabaseInterface>()
    val deaktiverNarmesteLederService = DeaktiverNarmesteLederService(
        nlResponseProducer,
        nlRequestProducer,
        arbeidsgiverService,
        pdlPersonService,
        database,
        syfonarmestelederClient
    )

    coEvery { arbeidsgiverService.getArbeidsgivere(any(), any()) } returns emptyList()

    describe("API for å deaktivere den sykmeldtes nærmeste leder - autentisering") {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            setUpAuth()
            application.routing {
                authenticate("loginservice") {
                    registrerNarmesteLederUserApi(deaktiverNarmesteLederService)
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
