package no.nav.syfo.forskuttering

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import no.nav.syfo.testutils.generateJWT
import no.nav.syfo.testutils.setUpAuth
import no.nav.syfo.testutils.setUpTestApplication
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

const val aktorIdMedForskuttering = 123
const val aktorIdUtenForskuttering = 999
const val aktorIdMedUkjentForskuttering = 678

object ForskutteringApiSpek : Spek({
    val httpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    val mockHttpServerPort = ServerSocket(0).use { it.localPort }
    val mockHttpServerUrl = "http://localhost:$mockHttpServerPort"
    val mockServer = embeddedServer(Netty, mockHttpServerPort) {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        routing {
            get("/api/$aktorIdMedForskuttering/forskuttering") {
                call.respond(HttpStatusCode.OK, ForskutteringRespons(Forskuttering.JA))
            }
            get("/api/$aktorIdUtenForskuttering/forskuttering") {
                call.respond(HttpStatusCode.OK, ForskutteringRespons(Forskuttering.NEI))
            }
            get("/api/$aktorIdMedUkjentForskuttering/forskuttering") {
                call.respond(HttpStatusCode.OK, ForskutteringRespons(Forskuttering.UKJENT))
            }
        }
    }.start()

    val forskutteringsClient = ForskutteringsClient(mockHttpServerUrl, "12345", httpClient)

    afterGroup {
        mockServer.stop(TimeUnit.SECONDS.toMillis(1), TimeUnit.SECONDS.toMillis(1))
    }

    describe("Forskutteringsapi returnerer gyldig svar for gyldig request") {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            val env = setUpAuth()
            application.routing {
                authenticate {
                    registrerForskutteringApi(forskutteringsClient)
                }
            }
            it("Returnerer JA hvis arbeidsgiver forskutterer") {
                with(
                    handleRequest(HttpMethod.Get, "/narmesteleder/arbeidsgiverForskutterer?aktorId=$aktorIdMedForskuttering&orgnummer=333") {
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
                with(
                    handleRequest(HttpMethod.Get, "/narmesteleder/arbeidsgiverForskutterer?aktorId=$aktorIdUtenForskuttering&orgnummer=333") {
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
                with(
                    handleRequest(HttpMethod.Get, "/narmesteleder/arbeidsgiverForskutterer?aktorId=$aktorIdMedUkjentForskuttering&orgnummer=333") {
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
        with(TestApplicationEngine()) {
            setUpTestApplication()
            val env = setUpAuth()
            application.routing {
                authenticate {
                    registrerForskutteringApi(forskutteringsClient)
                }
            }
            it("Returnerer feilmelding hvis aktørid mangler") {
                with(
                    handleRequest(HttpMethod.Get, "/narmesteleder/arbeidsgiverForskutterer?orgnummer=333") {
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
                    handleRequest(HttpMethod.Get, "/narmesteleder/arbeidsgiverForskutterer?aktorId=$aktorIdMedForskuttering") {
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
                    handleRequest(HttpMethod.Get, "/narmesteleder/arbeidsgiverForskutterer?aktorId=$aktorIdMedUkjentForskuttering&orgnummer=333") {
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
