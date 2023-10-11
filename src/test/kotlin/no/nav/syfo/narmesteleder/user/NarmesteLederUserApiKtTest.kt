package no.nav.syfo.narmesteleder.user

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.mockk.mockk
import kotlinx.coroutines.DelicateCoroutinesApi
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.narmesteleder.NarmesteLederService
import no.nav.syfo.narmesteleder.oppdatering.DeaktiverNarmesteLederService
import no.nav.syfo.narmesteleder.oppdatering.kafka.NLResponseProducer
import no.nav.syfo.testutils.generateJWT
import no.nav.syfo.testutils.setUpAuth
import no.nav.syfo.testutils.setUpTestApplication
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

@DelicateCoroutinesApi
internal class NarmesteLederUserApiKtTest {
    val nlResponseProducer = mockk<NLResponseProducer>(relaxed = true)
    val database = mockk<DatabaseInterface>()
    val deaktiverNarmesteLederService = DeaktiverNarmesteLederService(nlResponseProducer)
    val utvidetNarmesteLederService = NarmesteLederService(database)

    @Test
    internal fun `API for aa deaktivere den sykmeldtes nærmeste leder - aksepterer Authorization-header`() {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            setUpAuth()
            application.routing {
                authenticate("tokenx") {
                    registrerNarmesteLederUserApi(
                        deaktiverNarmesteLederService,
                        utvidetNarmesteLederService,
                    )
                }
            }
            with(
                handleRequest(HttpMethod.Post, "/9999/avkreft") {
                    addHeader(
                        "Authorization",
                        "Bearer ${
                            generateJWT(
                                consumerClientId = "issuer",
                                audience = "id",
                                subject = "12345678901",
                                issuer = "issuer",
                            )
                        }",
                    )
                },
            ) {
                response.status() shouldBeEqualTo HttpStatusCode.OK
            }
        }
    }

    @Test
    internal fun `API for å deaktivere den sykmeldtes nærmeste leder - Aksepterer ikke Authorization-header med feil issuer`() {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            setUpAuth()
            application.routing {
                authenticate("tokenx") {
                    registrerNarmesteLederUserApi(
                        deaktiverNarmesteLederService,
                        utvidetNarmesteLederService,
                    )
                }
            }
            with(
                handleRequest(HttpMethod.Post, "/9999/avkreft") {
                    addHeader(
                        "Authorization",
                        "Bearer ${
                            generateJWT(
                                consumerClientId = "issuer",
                                audience = "clientId",
                                subject = "12345678901",
                                issuer = "annenIssuer",
                            )
                        }",
                    )
                },
            ) {
                response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
            }
        }
    }

    @Test
    internal fun `API for å deaktivere den sykmeldtes nærmeste leder - Aksepterer ikke Authorization-header med feil audience`() {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            setUpAuth()
            application.routing {
                authenticate("tokenx") {
                    registrerNarmesteLederUserApi(
                        deaktiverNarmesteLederService,
                        utvidetNarmesteLederService,
                    )
                }
            }
            with(
                handleRequest(HttpMethod.Post, "/9999/avkreft") {
                    addHeader(
                        "Authorization",
                        "Bearer ${
                            generateJWT(
                                consumerClientId = "issuer",
                                audience = "feil",
                                subject = "12345678901",
                                issuer = "issuer",
                            )
                        }",
                    )
                },
            ) {
                response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
            }
        }
    }

    @Test
    internal fun `API for å deaktivere den sykmeldtes nærmeste leder - Aksepterer token fra cookie`() {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            setUpAuth()
            application.routing {
                authenticate("tokenx") {
                    registrerNarmesteLederUserApi(
                        deaktiverNarmesteLederService,
                        utvidetNarmesteLederService,
                    )
                }
            }
            with(
                handleRequest(HttpMethod.Post, "/9999/avkreft") {
                    addHeader(
                        "Cookie",
                        "selvbetjening-idtoken=${
                            generateJWT(
                                consumerClientId = "issuer",
                                audience = "id",
                                subject = "12345678901",
                                issuer = "issuer",
                            )
                        }",
                    )
                },
            ) {
                response.status() shouldBeEqualTo HttpStatusCode.OK
            }
        }
    }

    @Test
    internal fun `API for å deaktivere den sykmeldtes nærmeste leder - Aksepterer ikke token med feil issuer fra cookie`() {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            setUpAuth()
            application.routing {
                authenticate("tokenx") {
                    registrerNarmesteLederUserApi(
                        deaktiverNarmesteLederService,
                        utvidetNarmesteLederService,
                    )
                }
            }

            with(
                handleRequest(HttpMethod.Post, "/9999/avkreft") {
                    addHeader(
                        "Cookie",
                        "selvbetjening-idtoken=${
                            generateJWT(
                                consumerClientId = "issuer",
                                audience = "clientId",
                                subject = "12345678901",
                                issuer = "annenIssuer",
                            )
                        }",
                    )
                },
            ) {
                response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
            }
        }
    }

    @Test
    internal fun `API for å deaktivere den sykmeldtes nærmeste leder - Aksepterer ikke token med feil audience fra cookie`() {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            setUpAuth()
            application.routing {
                authenticate("tokenx") {
                    registrerNarmesteLederUserApi(
                        deaktiverNarmesteLederService,
                        utvidetNarmesteLederService,
                    )
                }
            }
            with(
                handleRequest(HttpMethod.Post, "/9999/avkreft") {
                    addHeader(
                        "Cookie",
                        "selvbetjening-idtoken=${
                            generateJWT(
                                consumerClientId = "issuer",
                                audience = "feil",
                                subject = "12345678901",
                                issuer = "issuer",
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
