package no.nav.syfo.narmesteleder.user

import com.auth0.jwt.interfaces.Payload
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.get
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.jackson.jackson
import io.ktor.request.header
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.util.KtorExperimentalAPI
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.narmesteleder.oppdatering.DeaktiverNarmesteLederService
import no.nav.syfo.testutils.generateJWTLoginservice
import no.nav.syfo.testutils.setUpAuth
import no.nav.syfo.testutils.setUpTestApplication
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

@KtorExperimentalAPI
class NarmesteLederUserApiKtTest : Spek({
    val deaktiverNarmesteLederService = mockk<DeaktiverNarmesteLederService>(relaxed = true)
    val mockPayload = mockk<Payload>()

    every { mockPayload.subject } returns "12345678901"

    /*val httpClient = HttpClient(Apache) {
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
            get("/get-cookie") {
                call.response.cookies.append(Cookie("selvbetjening-idtoken", generateJWTLoginservice(audience = "loginserviceId1", subject = "12345678901"), CookieEncoding.RAW))
                call.respondText("Cookie Set", ContentType.Text.Plain, HttpStatusCode.OK)
            }
        }
    }.start()

    afterGroup {
        mockServer.stop(TimeUnit.SECONDS.toMillis(1), TimeUnit.SECONDS.toMillis(1))
    }*/

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
                runBlocking {
                    with(
                        handleRequest(HttpMethod.Post, "/9999/avkreft") {
                            addHeader("Authorization", "Bearer ${getCookie(httpClient, mockHttpServerUrl)}")
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }
                }
            }*/
        }
    }
})

/*suspend fun getCookie(httpClient: HttpClient, mockHttpServerUrl: String): String {
    val respons = httpClient.get<String>("$mockHttpServerUrl/get-cookie") {
        accept(ContentType.Application.Json)
    }
    return respons.setCookie()[0].value
}*/
