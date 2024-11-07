package no.nav.syfo.narmesteleder.user

import io.ktor.client.request.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing
import io.ktor.server.testing.*
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
        testApplication {
            setUpTestApplication()
            setUpAuth()
            application {
                routing {
                    authenticate("tokenx") {
                        registrerNarmesteLederUserApi(
                            deaktiverNarmesteLederService,
                            utvidetNarmesteLederService,
                        )
                    }
                }
            }

            val response =
                client.post("/9999/avkreft") {
                    header(
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
                }

            response.status shouldBeEqualTo HttpStatusCode.OK
        }
    }

    @Test
    internal fun `API for å deaktivere den sykmeldtes nærmeste leder - Aksepterer ikke Authorization-header med feil issuer`() {
        testApplication {
            setUpTestApplication()
            setUpAuth()
            application {
                routing {
                    authenticate("tokenx") {
                        registrerNarmesteLederUserApi(
                            deaktiverNarmesteLederService,
                            utvidetNarmesteLederService,
                        )
                    }
                }
            }
            val response =
                client.post("/9999/avkreft") {
                    header(
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
                }

            response.status shouldBeEqualTo HttpStatusCode.Unauthorized
        }
    }

    @Test
    internal fun `API for å deaktivere den sykmeldtes nærmeste leder - Aksepterer ikke Authorization-header med feil audience`() {
        testApplication {
            setUpTestApplication()
            setUpAuth()
            application {
                routing {
                    authenticate("tokenx") {
                        registrerNarmesteLederUserApi(
                            deaktiverNarmesteLederService,
                            utvidetNarmesteLederService,
                        )
                    }
                }
            }

            val response =
                client.post("/9999/avkreft") {
                    header(
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
                }

            response.status shouldBeEqualTo HttpStatusCode.Unauthorized
        }
    }

    @Test
    internal fun `API for å deaktivere den sykmeldtes nærmeste leder - Aksepterer token fra cookie`() {
        testApplication {
            setUpTestApplication()
            setUpAuth()
            application {
                routing {
                    authenticate("tokenx") {
                        registrerNarmesteLederUserApi(
                            deaktiverNarmesteLederService,
                            utvidetNarmesteLederService,
                        )
                    }
                }
            }

            val response =
                client.post("/9999/avkreft") {
                    header(
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
                }

            response.status shouldBeEqualTo HttpStatusCode.OK
        }
    }

    @Test
    internal fun `API for å deaktivere den sykmeldtes nærmeste leder - Aksepterer ikke token med feil issuer fra cookie`() {
        testApplication {
            setUpTestApplication()
            setUpAuth()
            application {
                routing {
                    authenticate("tokenx") {
                        registrerNarmesteLederUserApi(
                            deaktiverNarmesteLederService,
                            utvidetNarmesteLederService,
                        )
                    }
                }
            }
            val response =
                client.post("/9999/avkreft") {
                    header(
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
                }

            response.status shouldBeEqualTo HttpStatusCode.Unauthorized
        }
    }

    @Test
    internal fun `API for å deaktivere den sykmeldtes nærmeste leder - Aksepterer ikke token med feil audience fra cookie`() {
        testApplication {
            setUpTestApplication()
            setUpAuth()
            application {
                routing {
                    authenticate("tokenx") {
                        registrerNarmesteLederUserApi(
                            deaktiverNarmesteLederService,
                            utvidetNarmesteLederService,
                        )
                    }
                }
            }
            val response =
                client.post("/9999/avkreft") {
                    header(
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
                }

            response.status shouldBeEqualTo HttpStatusCode.Unauthorized
        }
    }
}
