package no.nav.syfo.forskuttering

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.testutils.TestDB
import no.nav.syfo.testutils.dropData
import no.nav.syfo.testutils.generateJWT
import no.nav.syfo.testutils.lagreNarmesteleder
import no.nav.syfo.testutils.setUpAuth
import no.nav.syfo.testutils.setUpTestApplication
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ForskutteringApiTest {
    private val fnr = "12345678910"
    private val fnrNl = "01987654321"
    private val orgnummer = "33339999"
    private val testDb = TestDB()

    @AfterEach
    fun afterEach() {
        testDb.connection.dropData()
    }

    @AfterAll
    fun afterAll() {
        testDb.stop()
    }

    @Test
    internal fun `Forskutteringsapi returnerer gyldig svar for gyldig request returnerer JA hvis arbeidsgiver forskutterer`() {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            val env = setUpAuth()
            application.routing {
                authenticate("servicebruker") { registrerForskutteringApi(testDb) }
            }
            testDb.connection.lagreNarmesteleder(
                orgnummer,
                fnr,
                fnrNl,
                arbeidsgiverForskutterer = false,
                aktivFom = OffsetDateTime.now(ZoneOffset.UTC).minusYears(2),
                aktivTom = OffsetDateTime.now(ZoneOffset.UTC).minusYears(1),
                brukerNavn = "sykmeldt",
                narmestelederNavn = "narmesteleder",
            )
            testDb.connection.lagreNarmesteleder(
                orgnummer,
                fnr,
                fnrNl,
                arbeidsgiverForskutterer = true,
                brukerNavn = "sykmeldt",
                narmestelederNavn = "narmesteleder",
            )
            with(
                handleRequest(
                    HttpMethod.Get,
                    "/arbeidsgiver/forskutterer?orgnummer=$orgnummer",
                ) {
                    addHeader("Sykmeldt-Fnr", fnr)
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
                response.content?.shouldBeEqualTo("{\"forskuttering\":\"JA\"}")
            }
        }
    }

    @Test
    internal fun `Forskutteringsapi returnerer gyldig svar for gyldig request returnerer NEI hvis arbeidsgiver ikke forskutterer`() {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            val env = setUpAuth()
            application.routing {
                authenticate("servicebruker") { registrerForskutteringApi(testDb) }
            }
            testDb.connection.lagreNarmesteleder(
                orgnummer,
                fnr,
                fnrNl,
                arbeidsgiverForskutterer = false,
                brukerNavn = "sykmeldt",
                narmestelederNavn = "narmesteleder",
            )
            with(
                handleRequest(
                    HttpMethod.Get,
                    "/arbeidsgiver/forskutterer?orgnummer=$orgnummer",
                ) {
                    addHeader("Sykmeldt-Fnr", fnr)
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
                response.content?.shouldBeEqualTo("{\"forskuttering\":\"NEI\"}")
            }
        }
    }

    @Test
    internal fun `Forskutteringsapi returnerer gyldig svar for gyldig request returnerer UKJENT hvis vi ikke vet om arbeidsgiver forskutterer`() {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            val env = setUpAuth()
            application.routing {
                authenticate("servicebruker") { registrerForskutteringApi(testDb) }
            }
            testDb.connection.lagreNarmesteleder(
                orgnummer,
                fnr,
                fnrNl,
                arbeidsgiverForskutterer = null,
                brukerNavn = "sykmeldt",
                narmestelederNavn = "narmesteleder",
            )
            with(
                handleRequest(
                    HttpMethod.Get,
                    "/arbeidsgiver/forskutterer?orgnummer=$orgnummer",
                ) {
                    addHeader("Sykmeldt-Fnr", fnr)
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
                response.content?.shouldBeEqualTo("{\"forskuttering\":\"UKJENT\"}")
            }
        }
    }

    @Test
    internal fun `Forskutteringsapi returnerer gyldig svar for gyldig request returnerer UKJENT hvis bruker ikke har noen naermeste leder`() {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            val env = setUpAuth()
            application.routing {
                authenticate("servicebruker") { registrerForskutteringApi(testDb) }
            }
            with(
                handleRequest(
                    HttpMethod.Get,
                    "/arbeidsgiver/forskutterer?orgnummer=$orgnummer",
                ) {
                    addHeader("Sykmeldt-Fnr", fnr)
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
                response.content?.shouldBeEqualTo("{\"forskuttering\":\"UKJENT\"}")
            }
        }
    }

    @Test
    internal fun `Forskutteringsapi returnerer gyldig svar for gyldig request returnerer forskutteringsstatus for siste naermeste leder i samme orgnummer hvis naermeste leder ikke er aktiv`() {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            val env = setUpAuth()
            application.routing {
                authenticate("servicebruker") { registrerForskutteringApi(testDb) }
            }
            testDb.connection.lagreNarmesteleder(
                orgnummer,
                fnr,
                fnrNl,
                arbeidsgiverForskutterer = false,
                aktivFom = OffsetDateTime.now(ZoneOffset.UTC).minusYears(2),
                aktivTom = OffsetDateTime.now(ZoneOffset.UTC).minusYears(1),
                brukerNavn = "sykmeldt",
                narmestelederNavn = "narmesteleder",
            )
            testDb.connection.lagreNarmesteleder(
                orgnummer,
                fnr,
                fnrNl,
                arbeidsgiverForskutterer = true,
                aktivFom = OffsetDateTime.now(ZoneOffset.UTC).minusYears(1),
                aktivTom = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2),
                brukerNavn = "sykmeldt",
                narmestelederNavn = "narmesteleder",
            )
            with(
                handleRequest(
                    HttpMethod.Get,
                    "/arbeidsgiver/forskutterer?orgnummer=$orgnummer",
                ) {
                    addHeader("Sykmeldt-Fnr", fnr)
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
                response.content?.shouldBeEqualTo("{\"forskuttering\":\"JA\"}")
            }
        }
    }

    @Test
    internal fun `Feilhaandtering forskutteringsapi returnerer feilmelding hvis fnr mangler`() {
        testDb.connection.lagreNarmesteleder(
            orgnummer,
            fnr,
            fnrNl,
            arbeidsgiverForskutterer = true,
            brukerNavn = "sykmeldt",
            narmestelederNavn = "narmesteleder",
        )
        with(TestApplicationEngine()) {
            setUpTestApplication()
            val env = setUpAuth()
            application.routing {
                authenticate("servicebruker") { registrerForskutteringApi(testDb) }
            }
            with(
                handleRequest(HttpMethod.Get, "/arbeidsgiver/forskutterer?orgnummer=333") {
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
    }

    @Test
    internal fun `Feilhaandtering forskutteringsapi returnerer feilmelding hvis orgnummer mangler`() {
        testDb.connection.lagreNarmesteleder(
            orgnummer,
            fnr,
            fnrNl,
            arbeidsgiverForskutterer = true,
            brukerNavn = "sykmeldt",
            narmestelederNavn = "narmesteleder",
        )
        with(TestApplicationEngine()) {
            setUpTestApplication()
            val env = setUpAuth()
            application.routing {
                authenticate("servicebruker") { registrerForskutteringApi(testDb) }
            }
            with(
                handleRequest(HttpMethod.Get, "/arbeidsgiver/forskutterer") {
                    addHeader("Sykmeldt-Fnr", fnr)
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
    }

    @Test
    internal fun `Feilhaandtering forskutteringsapi returnerer feil audience gir feilmelding`() {
        testDb.connection.lagreNarmesteleder(
            orgnummer,
            fnr,
            fnrNl,
            arbeidsgiverForskutterer = true,
            brukerNavn = "sykmeldt",
            narmestelederNavn = "narmesteleder",
        )
        with(TestApplicationEngine()) {
            setUpTestApplication()
            val env = setUpAuth()
            application.routing {
                authenticate("servicebruker") { registrerForskutteringApi(testDb) }
            }
            with(
                handleRequest(HttpMethod.Get, "/arbeidsgiver/forskutterer?orgnummer=333") {
                    addHeader("Sykmeldt-Fnr", fnr)
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
