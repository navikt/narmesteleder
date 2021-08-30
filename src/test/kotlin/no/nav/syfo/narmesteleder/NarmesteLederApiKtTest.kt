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
import no.nav.syfo.narmesteleder.arbeidsforhold.service.ArbeidsgiverService
import no.nav.syfo.objectMapper
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.testutils.TestDB
import no.nav.syfo.testutils.dropData
import no.nav.syfo.testutils.generateJWT
import no.nav.syfo.testutils.lagreNarmesteleder
import no.nav.syfo.testutils.setUpAuth
import no.nav.syfo.testutils.setUpTestApplication
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

const val sykmeldtFnr = "fnr"
const val fnrLeder = "123"

@KtorExperimentalAPI
class NarmesteLederApiKtTest : Spek({
    val pdlPersonService = mockk<PdlPersonService>()
    val arbeidsgiverService = mockk<ArbeidsgiverService>(relaxed = true)
    val testDb = TestDB()
    val utvidetNarmesteLederService = NarmesteLederService(testDb, pdlPersonService)

    beforeEachTest {
        clearMocks(pdlPersonService)
        testDb.connection.lagreNarmesteleder(orgnummer = "orgnummer", fnr = sykmeldtFnr, fnrNl = fnrLeder, arbeidsgiverForskutterer = true)
    }
    afterEachTest {
        testDb.connection.dropData()
    }
    afterGroup {
        testDb.stop()
    }

    describe("API for å hente alle den sykmeldtes nærmeste ledere") {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            val env = setUpAuth()
            application.routing {
                authenticate("servicebruker") {
                    registrerNarmesteLederApi(testDb, utvidetNarmesteLederService)
                }
            }
            it("Returnerer nærmeste ledere") {
                with(
                    handleRequest(HttpMethod.Get, "/sykmeldt/narmesteledere") {
                        addHeader("Sykmeldt-Fnr", sykmeldtFnr)
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
                    val narmesteLedere = objectMapper.readValue<List<NarmesteLederRelasjon>>(response.content!!)
                    narmesteLedere.size shouldBeEqualTo 1
                    val narmesteLeder = narmesteLedere[0]
                    erLike(narmesteLeder, forventetNarmesteLeder()) shouldBeEqualTo true
                }
            }
            it("Returnerer inaktiv nærmeste leder") {
                testDb.connection.lagreNarmesteleder(
                    orgnummer = "orgnummer2", fnr = sykmeldtFnr, fnrNl = "fnrLeder2", arbeidsgiverForskutterer = true,
                    aktivTom = OffsetDateTime.now(
                        ZoneOffset.UTC
                    ).minusDays(2)
                )
                with(
                    handleRequest(HttpMethod.Get, "/sykmeldt/narmesteledere") {
                        addHeader("Sykmeldt-Fnr", sykmeldtFnr)
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
                    val narmesteLedere = objectMapper.readValue<List<NarmesteLederRelasjon>>(response.content!!)
                    narmesteLedere.size shouldBeEqualTo 2
                }
            }
            it("Returnerer tom liste hvis bruker ikke har noen nærmeste ledere") {
                with(
                    handleRequest(HttpMethod.Get, "/sykmeldt/narmesteledere") {
                        addHeader("Sykmeldt-Fnr", "sykmeldtFnrUtenNl")
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
                    val narmesteLedere = objectMapper.readValue<List<NarmesteLederRelasjon>>(response.content!!)
                    narmesteLedere.size shouldBeEqualTo 0
                }
            }
            it("Setter navn på lederne hvis utvidet == ja") {
                coEvery { pdlPersonService.getPersoner(any(), any()) } returns mapOf(
                    Pair(
                        fnrLeder,
                        PdlPerson(Navn("Fornavn", null, "Etternavn"), fnrLeder, "aktorid")
                    )
                )
                with(
                    handleRequest(
                        HttpMethod.Get,
                        "/sykmeldt/narmesteledere?utvidet=ja"
                    ) {
                        addHeader("Sykmeldt-Fnr", sykmeldtFnr)
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
                    val narmesteLedere = objectMapper.readValue<List<NarmesteLederRelasjon>>(response.content!!)
                    narmesteLedere.size shouldBeEqualTo 1
                    val narmesteLeder = narmesteLedere[0]
                    erLike(narmesteLeder, forventetNarmesteLeder(navn = "Fornavn Etternavn")) shouldBeEqualTo true
                }
            }
        }
    }

    describe("Feilhåndtering narmestelederapi") {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            val env = setUpAuth()
            application.routing {
                authenticate("servicebruker") {
                    registrerNarmesteLederApi(testDb, utvidetNarmesteLederService)
                }
            }
            it("Returnerer feilmelding hvis fnr for den sykmeldte mangler") {
                with(
                    handleRequest(HttpMethod.Get, "/sykmeldt/narmesteledere") {
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
                    handleRequest(HttpMethod.Get, "/sykmeldt/narmesteledere") {
                        addHeader("Sykmeldt-Fnr", sykmeldtFnr)
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

private fun erLike(narmesteLederRelasjon1: NarmesteLederRelasjon, narmesteLederRelasjon2: NarmesteLederRelasjon): Boolean {
    val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
    return narmesteLederRelasjon1.copy(narmesteLederId = narmesteLederRelasjon2.narmesteLederId, timestamp = timestamp) == narmesteLederRelasjon2.copy(timestamp = timestamp)
}

private fun forventetNarmesteLeder(navn: String? = null): NarmesteLederRelasjon =
    NarmesteLederRelasjon(
        narmesteLederId = UUID.randomUUID(),
        fnr = sykmeldtFnr,
        orgnummer = "orgnummer",
        narmesteLederFnr = fnrLeder,
        narmesteLederTelefonnummer = "90909090",
        narmesteLederEpost = "epost@nav.no",
        aktivFom = LocalDate.now().minusYears(1),
        aktivTom = null,
        arbeidsgiverForskutterer = true,
        skrivetilgang = true,
        tilganger = listOf(Tilgang.SYKMELDING, Tilgang.SYKEPENGESOKNAD, Tilgang.MOTE, Tilgang.OPPFOLGINGSPLAN),
        navn = navn,
        timestamp = OffsetDateTime.now(ZoneOffset.UTC)
    )
