package no.nav.syfo.forskuttering

import io.ktor.auth.authenticate
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
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
import java.time.OffsetDateTime
import java.time.ZoneOffset

object ForskutteringApiSpek : Spek({
    val fnr = "12345678910"
    val fnrNl = "01987654321"
    val orgnummer = "33339999"
    val testDb = TestDB()

    afterEachTest {
        testDb.connection.dropData()
    }

    afterGroup {
        testDb.stop()
    }

    describe("Forskutteringsapi returnerer gyldig svar for gyldig request") {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            val env = setUpAuth()
            application.routing {
                authenticate {
                    registrerForskutteringApi(testDb)
                }
            }
            it("Returnerer JA hvis arbeidsgiver forskutterer") {
                testDb.connection.lagreNarmesteleder(orgnummer, fnr, fnrNl, arbeidsgiverForskutterer = true)
                with(
                    handleRequest(HttpMethod.Get, "/arbeidsgiverForskutterer?orgnummer=$orgnummer") {
                        addHeader("fnr", fnr)
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
                    response.content?.shouldBeEqualTo("{\"forskuttering\":\"JA\"}")
                }
            }
            it("Returnerer NEI hvis arbeidsgiver ikke forskutterer") {
                testDb.connection.lagreNarmesteleder(orgnummer, fnr, fnrNl, arbeidsgiverForskutterer = false)
                with(
                    handleRequest(HttpMethod.Get, "/arbeidsgiverForskutterer?orgnummer=$orgnummer") {
                        addHeader("fnr", fnr)
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
                    response.content?.shouldBeEqualTo("{\"forskuttering\":\"NEI\"}")
                }
            }
            it("Returnerer UKJENT hvis vi ikke vet om arbeidsgiver forskutterer") {
                testDb.connection.lagreNarmesteleder(orgnummer, fnr, fnrNl, arbeidsgiverForskutterer = null)
                with(
                    handleRequest(HttpMethod.Get, "/arbeidsgiverForskutterer?orgnummer=$orgnummer") {
                        addHeader("fnr", fnr)
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
                    response.content?.shouldBeEqualTo("{\"forskuttering\":\"UKJENT\"}")
                }
            }
            it("Returnerer UKJENT hvis brukerikke har noen nærmeste leder") {
                with(
                    handleRequest(HttpMethod.Get, "/arbeidsgiverForskutterer?orgnummer=$orgnummer") {
                        addHeader("fnr", fnr)
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
                    response.content?.shouldBeEqualTo("{\"forskuttering\":\"UKJENT\"}")
                }
            }
            it("Returnerer UKJENT hvis nærmeste leder ikke er aktiv") {
                testDb.connection.lagreNarmesteleder(orgnummer, fnr, fnrNl, arbeidsgiverForskutterer = true, aktivTom = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2))
                with(
                    handleRequest(HttpMethod.Get, "/arbeidsgiverForskutterer?orgnummer=$orgnummer") {
                        addHeader("fnr", fnr)
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
                    response.content?.shouldBeEqualTo("{\"forskuttering\":\"UKJENT\"}")
                }
            }
        }
    }

    describe("Feilhåndtering forskutteringsapi") {
        testDb.connection.lagreNarmesteleder(orgnummer, fnr, fnrNl, arbeidsgiverForskutterer = true)
        with(TestApplicationEngine()) {
            setUpTestApplication()
            val env = setUpAuth()
            application.routing {
                authenticate {
                    registrerForskutteringApi(testDb)
                }
            }
            it("Returnerer feilmelding hvis fnr mangler") {
                with(
                    handleRequest(HttpMethod.Get, "/arbeidsgiverForskutterer?orgnummer=333") {
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
            it("Returnerer feilmelding hvis orgnummer mangler") {
                with(
                    handleRequest(HttpMethod.Get, "/arbeidsgiverForskutterer") {
                        addHeader("fnr", fnr)
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
                    handleRequest(HttpMethod.Get, "/arbeidsgiverForskutterer?orgnummer=333") {
                        addHeader("fnr", fnr)
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
