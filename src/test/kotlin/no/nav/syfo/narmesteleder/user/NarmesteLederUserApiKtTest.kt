package no.nav.syfo.narmesteleder.user

import com.auth0.jwt.interfaces.Payload
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.util.KtorExperimentalAPI
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.narmesteleder.oppdatering.DeaktiverNarmesteLederService
import no.nav.syfo.testutils.setUpAuth
import no.nav.syfo.testutils.setUpTestApplication
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
class NarmesteLederUserApiKtTest : Spek({
    val deaktiverNarmesteLederService = mockk<DeaktiverNarmesteLederService>(relaxed = true)
    val mockPayload = mockk<Payload>()

    every { mockPayload.subject } returns "12345678901"

    describe("API for å deaktivere den sykmeldtes nærmeste leder - autentisering") {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            setUpAuth()
            application.routing {
                authenticate("loginservice") {
                    registrerNarmesteLederUserApi(deaktiverNarmesteLederService)
                }
            }
            it("Aksepterer Authorization-header") {
                with(
                    handleRequest(HttpMethod.Post, "/9999/avkreft") {
                        addHeader("Authorization", "Bearer token")
                        call.authentication.principal = JWTPrincipal(mockPayload)
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                }
            }
            /*it("Aksepterer token fra cookie") {
                with(
                    handleRequest(HttpMethod.Post, "/9999/avkreft") {
                        call.request.cookies.append(Cookie("selvbetjening-idtoken", token.serialize(), CookieEncoding.RAW, domain = domain, path = "/"))
                        call.authentication.principal = JWTPrincipal(mockPayload)
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                }
            }*/
        }
    }
})
