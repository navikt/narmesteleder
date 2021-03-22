package no.nav.syfo.narmesteleder

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.auth.authenticate
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.objectMapper
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.testutils.HttpClientTest
import no.nav.syfo.testutils.ResponseData
import no.nav.syfo.testutils.generateJWT
import no.nav.syfo.testutils.setUpAuth
import no.nav.syfo.testutils.setUpTestApplication
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

const val sykmeldtAktorId = "aktorId"
const val aktorIdLeder = "123"

@KtorExperimentalAPI
class NarmesteLederApiKtTest : Spek({
    val pdlPersonService = mockk<PdlPersonService>()
    val httpClient = HttpClientTest()
    httpClient.responseData = ResponseData(HttpStatusCode.NotFound, "")

    val narmesteLederClient = NarmesteLederClient("url", "12345", httpClient.httpClient)
    val utvidetNarmesteLederService = UtvidetNarmesteLederService(narmesteLederClient, pdlPersonService)

    beforeEachTest {
        clearMocks(pdlPersonService)
    }

    describe("API for å hente alle den sykmeldtes nærmeste ledere") {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            val env = setUpAuth()
            application.routing {
                authenticate {
                    registrerNarmesteLederApi(narmesteLederClient, utvidetNarmesteLederService)
                }
            }
            it("Returnerer nærmeste ledere") {
                httpClient.respond(narmestelederResponse())
                with(
                    handleRequest(HttpMethod.Get, "/narmesteleder/sykmeldt/$sykmeldtAktorId/narmesteledere") {
                        addHeader(
                            HttpHeaders.Authorization,
                            "Bearer ${
                            generateJWT(
                                "syfosmaltinn",
                                "narmesteleder",
                                subject = "123",
                                issuer = env.jwtIssuer
                            )
                            }"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    objectMapper.readValue<List<NarmesteLederRelasjon>>(response.content!!) shouldBeEqualTo listOf(
                        narmesteLederRelasjon()
                    )
                }
            }
            it("Setter navn på lederne hvis utvidet == ja") {
                coEvery { pdlPersonService.getPersonnavn(any(), any()) } returns mapOf(
                    Pair(
                        aktorIdLeder,
                        Navn("Fornavn", null, "Etternavn")
                    )
                )
                httpClient.respond(narmestelederResponse())
                with(
                    handleRequest(
                        HttpMethod.Get,
                        "/narmesteleder/sykmeldt/$sykmeldtAktorId/narmesteledere?utvidet=ja"
                    ) {
                        addHeader(
                            HttpHeaders.Authorization,
                            "Bearer ${
                            generateJWT(
                                "syfosmaltinn",
                                "narmesteleder",
                                subject = "123",
                                issuer = env.jwtIssuer
                            )
                            }"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    objectMapper.readValue<List<NarmesteLederRelasjon>>(response.content!!) shouldBeEqualTo listOf(
                        narmesteLederRelasjon(navn = "Fornavn Etternavn")
                    )
                }
            }
        }
    }

    describe("Feilhåndtering narmestelederapi") {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            val env = setUpAuth()
            application.routing {
                authenticate {
                    registrerNarmesteLederApi(narmesteLederClient, utvidetNarmesteLederService)
                }
            }
            it("Returnerer feilmelding hvis aktørid for den sykmeldte mangler") {
                with(
                    handleRequest(HttpMethod.Get, "/narmesteleder/sykmeldt/narmesteledere") {
                        addHeader(
                            HttpHeaders.Authorization,
                            "Bearer ${
                            generateJWT(
                                "syfosmaltinn",
                                "narmesteleder",
                                subject = "123",
                                issuer = env.jwtIssuer
                            )
                            }"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                    response.content shouldNotBeEqualTo null
                }
            }
            it("Feil audience gir feilmelding") {
                with(
                    handleRequest(HttpMethod.Get, "/narmesteleder/sykmeldt/$sykmeldtAktorId/narmesteledere") {
                        addHeader(
                            HttpHeaders.Authorization,
                            "Bearer ${
                            generateJWT(
                                "syfosmaltinn",
                                "feil",
                                subject = "123",
                                issuer = env.jwtIssuer
                            )
                            }"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }
        }
    }
})

private fun narmestelederResponse(): List<NarmesteLeder> =
    listOf(
        NarmesteLeder(
            aktorId = sykmeldtAktorId,
            orgnummer = "orgnummer",
            nlAktorId = aktorIdLeder,
            nlTelefonnummer = null,
            nlEpost = "epost@nav.no",
            aktivFom = LocalDate.now().minusYears(1),
            aktivTom = null,
            agForskutterer = true
        )
    )

private fun narmesteLederRelasjon(navn: String? = null) =
    NarmesteLederRelasjon(
        aktorId = sykmeldtAktorId,
        orgnummer = "orgnummer",
        narmesteLederAktorId = aktorIdLeder,
        narmesteLederTelefonnummer = null,
        narmesteLederEpost = "epost@nav.no",
        aktivFom = LocalDate.now().minusYears(1),
        aktivTom = null,
        arbeidsgiverForskutterer = true,
        skrivetilgang = true,
        tilganger = listOf(Tilgang.SYKMELDING, Tilgang.SYKEPENGESOKNAD, Tilgang.MOTE, Tilgang.OPPFOLGINGSPLAN),
        navn = navn
    )
