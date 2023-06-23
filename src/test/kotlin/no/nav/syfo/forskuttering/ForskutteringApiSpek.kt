package no.nav.syfo.forskuttering

import io.kotest.core.spec.style.FunSpec
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

class ForskutteringApiSpek :
    FunSpec({
        val fnr = "12345678910"
        val fnrNl = "01987654321"
        val orgnummer = "33339999"
        val testDb = TestDB()

        afterTest { testDb.connection.dropData() }

        afterSpec { testDb.stop() }

        context("Forskutteringsapi returnerer gyldig svar for gyldig request") {
            with(TestApplicationEngine()) {
                setUpTestApplication()
                val env = setUpAuth()
                application.routing {
                    authenticate("servicebruker") { registrerForskutteringApi(testDb) }
                }
                test("Returnerer JA hvis arbeidsgiver forskutterer") {
                    testDb.connection.lagreNarmesteleder(
                        orgnummer,
                        fnr,
                        fnrNl,
                        arbeidsgiverForskutterer = false,
                        aktivFom = OffsetDateTime.now(ZoneOffset.UTC).minusYears(2),
                        aktivTom = OffsetDateTime.now(ZoneOffset.UTC).minusYears(1)
                    )
                    testDb.connection.lagreNarmesteleder(
                        orgnummer,
                        fnr,
                        fnrNl,
                        arbeidsgiverForskutterer = true
                    )
                    with(
                        handleRequest(
                            HttpMethod.Get,
                            "/arbeidsgiver/forskutterer?orgnummer=$orgnummer"
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
                test("Returnerer NEI hvis arbeidsgiver ikke forskutterer") {
                    testDb.connection.lagreNarmesteleder(
                        orgnummer,
                        fnr,
                        fnrNl,
                        arbeidsgiverForskutterer = false
                    )
                    with(
                        handleRequest(
                            HttpMethod.Get,
                            "/arbeidsgiver/forskutterer?orgnummer=$orgnummer"
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
                test("Returnerer UKJENT hvis vi ikke vet om arbeidsgiver forskutterer") {
                    testDb.connection.lagreNarmesteleder(
                        orgnummer,
                        fnr,
                        fnrNl,
                        arbeidsgiverForskutterer = null
                    )
                    with(
                        handleRequest(
                            HttpMethod.Get,
                            "/arbeidsgiver/forskutterer?orgnummer=$orgnummer"
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
                test("Returnerer UKJENT hvis bruker ikke har noen nærmeste leder") {
                    with(
                        handleRequest(
                            HttpMethod.Get,
                            "/arbeidsgiver/forskutterer?orgnummer=$orgnummer"
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
                test(
                    "Returnerer forskutteringsstatus for siste nærmeste leder i samme orgnummer hvis nærmeste leder ikke er aktiv"
                ) {
                    testDb.connection.lagreNarmesteleder(
                        orgnummer,
                        fnr,
                        fnrNl,
                        arbeidsgiverForskutterer = false,
                        aktivFom = OffsetDateTime.now(ZoneOffset.UTC).minusYears(2),
                        aktivTom = OffsetDateTime.now(ZoneOffset.UTC).minusYears(1)
                    )
                    testDb.connection.lagreNarmesteleder(
                        orgnummer,
                        fnr,
                        fnrNl,
                        arbeidsgiverForskutterer = true,
                        aktivFom = OffsetDateTime.now(ZoneOffset.UTC).minusYears(1),
                        aktivTom = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2)
                    )
                    with(
                        handleRequest(
                            HttpMethod.Get,
                            "/arbeidsgiver/forskutterer?orgnummer=$orgnummer"
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
        }

        context("Feilhåndtering forskutteringsapi") {
            testDb.connection.lagreNarmesteleder(
                orgnummer,
                fnr,
                fnrNl,
                arbeidsgiverForskutterer = true
            )
            with(TestApplicationEngine()) {
                setUpTestApplication()
                val env = setUpAuth()
                application.routing {
                    authenticate("servicebruker") { registrerForskutteringApi(testDb) }
                }
                test("Returnerer feilmelding hvis fnr mangler") {
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
                test("Returnerer feilmelding hvis orgnummer mangler") {
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
                test("Feil audience gir feilmelding") {
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
    })
