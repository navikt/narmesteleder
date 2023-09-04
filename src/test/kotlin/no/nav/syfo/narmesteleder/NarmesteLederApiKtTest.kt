package no.nav.syfo.narmesteleder

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FunSpec
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.DelicateCoroutinesApi
import no.nav.syfo.objectMapper
import no.nav.syfo.testutils.TestDB
import no.nav.syfo.testutils.dropData
import no.nav.syfo.testutils.generateJWT
import no.nav.syfo.testutils.lagreNarmesteleder
import no.nav.syfo.testutils.setUpAuth
import no.nav.syfo.testutils.setUpTestApplication
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo

const val sykmeldtFnr = "fnr"
const val fnrLeder = "123"

@DelicateCoroutinesApi
class NarmesteLederApiKtTest :
    FunSpec({
        val testDb = TestDB()
        val utvidetNarmesteLederService = NarmesteLederService(testDb)

        beforeTest {
            testDb.connection.dropData()
            testDb.connection.lagreNarmesteleder(
                orgnummer = "orgnummer",
                fnr = sykmeldtFnr,
                fnrNl = fnrLeder,
                arbeidsgiverForskutterer = true,
                brukerNavn = "sykmeldt",
                narmestelederNavn = "narmesteleder"
            )
        }

        afterSpec { testDb.stop() }

        context("API for å hente alle den sykmeldtes nærmeste ledere") {
            with(TestApplicationEngine()) {
                setUpTestApplication()
                val env = setUpAuth()
                application.routing {
                    authenticate("servicebruker") {
                        registrerNarmesteLederApi(testDb, utvidetNarmesteLederService)
                    }
                }
                test("Returnerer nærmeste ledere") {
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
                                    issuer = env.jwtIssuer,
                                )
                            }",
                            )
                        },
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val narmesteLedere =
                            objectMapper.readValue<List<NarmesteLederRelasjon>>(response.content!!)
                        narmesteLedere.size shouldBeEqualTo 1
                        val narmesteLeder = narmesteLedere[0]
                        erLike(
                            narmesteLeder,
                            forventetNarmesteLeder("narmesteleder")
                        ) shouldBeEqualTo true
                    }
                }
                test("Returnerer inaktiv nærmeste leder") {
                    testDb.connection.lagreNarmesteleder(
                        orgnummer = "orgnummer2",
                        fnr = sykmeldtFnr,
                        fnrNl = "fnrLeder2",
                        arbeidsgiverForskutterer = true,
                        aktivTom =
                            OffsetDateTime.now(
                                    ZoneOffset.UTC,
                                )
                                .minusDays(2),
                        brukerNavn = "sykmeldt",
                        narmestelederNavn = "narmesteleder",
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
                                    issuer = env.jwtIssuer,
                                )
                            }",
                            )
                        },
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val narmesteLedere =
                            objectMapper.readValue<List<NarmesteLederRelasjon>>(response.content!!)
                        narmesteLedere.size shouldBeEqualTo 2
                    }
                }
                test("Returnerer tom liste hvis bruker ikke har noen nærmeste ledere") {
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
                                    issuer = env.jwtIssuer,
                                )
                            }",
                            )
                        },
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val narmesteLedere =
                            objectMapper.readValue<List<NarmesteLederRelasjon>>(response.content!!)
                        narmesteLedere.size shouldBeEqualTo 0
                    }
                }
                test("Setter navn på lederne hvis utvidet == ja") {
                    with(
                        handleRequest(
                            HttpMethod.Get,
                            "/sykmeldt/narmesteledere?utvidet=ja",
                        ) {
                            addHeader("Sykmeldt-Fnr", sykmeldtFnr)
                            addHeader(
                                HttpHeaders.Authorization,
                                "Bearer ${
                                generateJWT(
                                    "syfosmaltinn",
                                    "narmesteleder",
                                    subject = "123",
                                    issuer = env.jwtIssuer,
                                )
                            }",
                            )
                        },
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val narmesteLedere =
                            objectMapper.readValue<List<NarmesteLederRelasjon>>(response.content!!)
                        narmesteLedere.size shouldBeEqualTo 1
                        val narmesteLeder = narmesteLedere[0]
                        erLike(
                            narmesteLeder,
                            forventetNarmesteLeder(navn = "narmesteleder")
                        ) shouldBeEqualTo true
                    }
                }
            }
        }

        context("Feilhåndtering narmestelederapi") {
            with(TestApplicationEngine()) {
                setUpTestApplication()
                val env = setUpAuth()
                application.routing {
                    authenticate("servicebruker") {
                        registrerNarmesteLederApi(testDb, utvidetNarmesteLederService)
                    }
                }
                test("Returnerer feilmelding hvis fnr for den sykmeldte mangler") {
                    with(
                        handleRequest(HttpMethod.Get, "/sykmeldt/narmesteledere") {
                            addHeader(
                                HttpHeaders.Authorization,
                                "Bearer ${
                                generateJWT(
                                    "syfosmaltinn",
                                    "narmesteleder",
                                    subject = "123",
                                    issuer = env.jwtIssuer,
                                )
                            }",
                            )
                        },
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        response.content shouldNotBeEqualTo null
                    }
                }
                test("Feil audience gir feilmelding") {
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
                                    issuer = env.jwtIssuer,
                                )
                            }",
                            )
                        },
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                    }
                }
            }
        }
    })

private fun erLike(
    narmesteLederRelasjon1: NarmesteLederRelasjon,
    narmesteLederRelasjon2: NarmesteLederRelasjon
): Boolean {
    val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
    return narmesteLederRelasjon1.copy(
        narmesteLederId = narmesteLederRelasjon2.narmesteLederId,
        timestamp = timestamp
    ) == narmesteLederRelasjon2.copy(timestamp = timestamp)
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
        tilganger =
            listOf(
                Tilgang.SYKMELDING,
                Tilgang.SYKEPENGESOKNAD,
                Tilgang.MOTE,
                Tilgang.OPPFOLGINGSPLAN
            ),
        navn = navn,
        timestamp = OffsetDateTime.now(ZoneOffset.UTC),
    )
