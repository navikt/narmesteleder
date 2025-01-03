package no.nav.syfo.narmesteleder

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing
import io.ktor.server.testing.*
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
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

const val sykmeldtFnr = "fnr"
const val fnrLeder = "123"

@DelicateCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class NarmesteLederApiKtTest {
    val testDb = TestDB()
    val utvidetNarmesteLederService = NarmesteLederService(testDb)

    @BeforeEach
    fun beforeEach() {
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

    @AfterAll
    fun afterAll() {
        testDb.stop()
    }

    @Test
    internal fun `API for å hente alle den sykmeldtes nærmeste leder Returnerer nærmeste ledere`() {
        testApplication {
            setUpTestApplication()
            val env = setUpAuth()
            application {
                routing {
                    authenticate("servicebruker") {
                        registrerNarmesteLederApi(testDb, utvidetNarmesteLederService)
                    }
                }
            }

            val response =
                client.get("/sykmeldt/narmesteledere") {
                    header("Sykmeldt-Fnr", sykmeldtFnr)
                    header(
                        HttpHeaders.Authorization,
                        "Bearer ${
                        generateJWT(
                            "syfosmaltinn",
                            "narmesteleder",
                            subject = "123",
                            issuer = env.jwtIssuer,
                        )
                    }"
                    )
                }

            response.status shouldBeEqualTo HttpStatusCode.OK
            val narmesteLedere =
                objectMapper.readValue<List<NarmesteLederRelasjon>>(response.bodyAsText())
            narmesteLedere.size shouldBeEqualTo 1
            val narmesteLeder = narmesteLedere[0]
            erLike(narmesteLeder, forventetNarmesteLeder("narmesteleder")) shouldBeEqualTo true
        }
    }

    @Test
    internal fun `API for a hente alle den sykmeldtes nærmeste ledere returnerer inaktiv nærmeste leder`() {
        testApplication {
            setUpTestApplication()
            val env = setUpAuth()
            application {
                routing {
                    authenticate("servicebruker") {
                        registrerNarmesteLederApi(testDb, utvidetNarmesteLederService)
                    }
                }
            }
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

            val response =
                client.get("/sykmeldt/narmesteledere") {
                    header("Sykmeldt-Fnr", sykmeldtFnr)
                    header(
                        HttpHeaders.Authorization,
                        "Bearer ${
                            generateJWT(
                                "syfosmaltinn",
                                "narmesteleder",
                                subject = "123",
                                issuer = env.jwtIssuer,
                            )
                        }"
                    )
                }

            response.status shouldBeEqualTo HttpStatusCode.OK
            val narmesteLedere =
                objectMapper.readValue<List<NarmesteLederRelasjon>>(response.bodyAsText())
            narmesteLedere.size shouldBeEqualTo 2
        }
    }

    @Test
    internal fun `API for a hente alle den sykmeldtes nærmeste ledere eeturnerer tom liste hvis bruker ikke har noen nærmeste lederer`() {
        testApplication {
            setUpTestApplication()
            val env = setUpAuth()
            application {
                routing {
                    authenticate("servicebruker") {
                        registrerNarmesteLederApi(testDb, utvidetNarmesteLederService)
                    }
                }
            }

            val response =
                client.get("/sykmeldt/narmesteledere") {
                    header("Sykmeldt-Fnr", "sykmeldtFnrUtenNl")
                    header(
                        HttpHeaders.Authorization,
                        "Bearer ${
                        generateJWT(
                            "syfosmaltinn",
                            "narmesteleder",
                            subject = "123",
                            issuer = env.jwtIssuer,
                        )
                    }"
                    )
                }

            response.status shouldBeEqualTo HttpStatusCode.OK
            val narmesteLedere =
                objectMapper.readValue<List<NarmesteLederRelasjon>>(response.bodyAsText())
            narmesteLedere.size shouldBeEqualTo 0
        }
    }

    @Test
    internal fun `API for a hente alle den sykmeldtes nærmeste ledere setter navn på lederne hvis utvidet == ja`() {
        testApplication {
            setUpTestApplication()
            val env = setUpAuth()
            application {
                routing {
                    authenticate("servicebruker") {
                        registrerNarmesteLederApi(testDb, utvidetNarmesteLederService)
                    }
                }
            }

            val response =
                client.get("/sykmeldt/narmesteledere?utvidet=ja") {
                    header("Sykmeldt-Fnr", sykmeldtFnr)
                    header(
                        HttpHeaders.Authorization,
                        "Bearer ${
                        generateJWT(
                            "syfosmaltinn",
                            "narmesteleder",
                            subject = "123",
                            issuer = env.jwtIssuer,
                        )
                    }"
                    )
                }

            response.status shouldBeEqualTo HttpStatusCode.OK
            val narmesteLedere =
                objectMapper.readValue<List<NarmesteLederRelasjon>>(response.bodyAsText())
            narmesteLedere.size shouldBeEqualTo 1
            val narmesteLeder = narmesteLedere[0]
            erLike(narmesteLeder, forventetNarmesteLeder(navn = "narmesteleder")) shouldBeEqualTo
                true
        }
    }

    @Test
    internal fun `Feilhåndtering narmestelederapi returnerer feilmelding hvis fnr for den sykmeldte mangler`() {
        testApplication {
            setUpTestApplication()
            val env = setUpAuth()
            application {
                routing {
                    authenticate("servicebruker") {
                        registrerNarmesteLederApi(testDb, utvidetNarmesteLederService)
                    }
                }
            }

            val response =
                client.get("/sykmeldt/narmesteledere") {
                    header(
                        HttpHeaders.Authorization,
                        "Bearer ${
                        generateJWT(
                            "syfosmaltinn",
                            "narmesteleder",
                            subject = "123",
                            issuer = env.jwtIssuer,
                        )
                    }"
                    )
                }

            response.status shouldBeEqualTo HttpStatusCode.BadRequest
            response.bodyAsText() shouldNotBeEqualTo null
        }
    }

    @Test
    internal fun `Feilhåndtering narmestelederapi returnerer returnerer feilmelding hvis fnr for den sykmeldte mangler`() {
        testApplication {
            setUpTestApplication()
            val env = setUpAuth()
            application {
                routing {
                    authenticate("servicebruker") {
                        registrerNarmesteLederApi(testDb, utvidetNarmesteLederService)
                    }
                }
            }

            val response =
                client.get("/sykmeldt/narmesteledere") {
                    header(
                        HttpHeaders.Authorization,
                        "Bearer ${
                        generateJWT(
                            "syfosmaltinn",
                            "narmesteleder",
                            subject = "123",
                            issuer = env.jwtIssuer,
                        )
                    }"
                    )
                }

            response.status shouldBeEqualTo HttpStatusCode.BadRequest
            response.bodyAsText() shouldNotBeEqualTo null
        }
    }

    @Test
    internal fun `Feilhåndtering narmestelederapi returnerer feil audience gir feilmelding`() {
        testApplication {
            setUpTestApplication()
            val env = setUpAuth()
            application {
                routing {
                    authenticate("servicebruker") {
                        registrerNarmesteLederApi(testDb, utvidetNarmesteLederService)
                    }
                }
            }

            val response =
                client.get("/sykmeldt/narmesteledere") {
                    header("Sykmeldt-Fnr", sykmeldtFnr)
                    header(
                        HttpHeaders.Authorization,
                        "Bearer ${
                        generateJWT(
                            "syfosmaltinn",
                            "feil",
                            subject = "123",
                            issuer = env.jwtIssuer,
                        )
                    }"
                    )
                }
            response.status shouldBeEqualTo HttpStatusCode.Unauthorized
        }
    }
}

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
