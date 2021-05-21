package no.nav.syfo.narmesteleder.leder

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.auth.authenticate
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.narmesteleder.NarmesteLederService
import no.nav.syfo.narmesteleder.fnrLeder
import no.nav.syfo.narmesteleder.leder.model.AnsattResponse
import no.nav.syfo.narmesteleder.oppdatering.DeaktiverNarmesteLederService
import no.nav.syfo.narmesteleder.sykmeldtFnr
import no.nav.syfo.narmesteleder.user.registrerNarmesteLederUserArbeidsgiverApi
import no.nav.syfo.objectMapper
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.testutils.TestDB
import no.nav.syfo.testutils.dropData
import no.nav.syfo.testutils.generateJWTLoginservice
import no.nav.syfo.testutils.lagreNarmesteleder
import no.nav.syfo.testutils.setUpAuth
import no.nav.syfo.testutils.setUpTestApplication
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class LederApiKtTest : Spek({
    val pdlPersonService = mockk<PdlPersonService>()
    val testDb = TestDB()
    val narmestelederService = NarmesteLederService(testDb, pdlPersonService)
    val deaktiverNarmesteLederService = mockk<DeaktiverNarmesteLederService>(relaxed = true)
    beforeEachTest {
        clearMocks(pdlPersonService)
        testDb.connection.lagreNarmesteleder(
            orgnummer = "orgnummer",
            fnr = sykmeldtFnr,
            fnrNl = fnrLeder,
            arbeidsgiverForskutterer = true
        )
        coEvery { pdlPersonService.getPersoner(any(), any()) } returns mapOf(
            Pair(
                sykmeldtFnr,
                PdlPerson(Navn("Fornavn", null, "Etternavn"), fnrLeder, "aktorid")
            )
        )
    }
    afterEachTest {
        testDb.connection.dropData()
    }
    afterGroup {
        testDb.stop()
    }

    describe("get nl relasjoner for leder") {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            setUpAuth()
            application.routing {
                authenticate("loginservice") {
                    registrerNarmesteLederUserArbeidsgiverApi(
                        deaktiverNarmesteLederService,
                        narmestelederService
                    )
                }
            }
            it("get empty list") {
                with(
                    handleRequest(HttpMethod.Get, "/arbeidsgiver/ansatte") {
                        addHeader("Cookie", "selvbetjening-idtoken=${generateJWTLoginservice(audience = "loginserviceId1", subject = "12345678902", issuer = "issuer")}")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                }
            }
            it("get list of relasjoner") {
                testDb.connection.lagreNarmesteleder(
                    orgnummer = "orgnummer",
                    fnr = sykmeldtFnr,
                    fnrNl = fnrLeder,
                    arbeidsgiverForskutterer = true
                )
                testDb.connection.lagreNarmesteleder(
                    orgnummer = "orgnummer",
                    fnr = "fnr2",
                    fnrNl = fnrLeder,
                    arbeidsgiverForskutterer = true
                )
                with(
                    handleRequest(HttpMethod.Get, "/arbeidsgiver/ansatte") {
                        addHeader("Cookie", "selvbetjening-idtoken=${generateJWTLoginservice(audience = "loginserviceId1", subject = fnrLeder, issuer = "issuer")}")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    val relasjoner: AnsattResponse? = response.content?.let { objectMapper.readValue(it) }
                    relasjoner shouldNotBe null
                    relasjoner!!.ansatte.size shouldBeEqualTo 3
                }
            }
        }
    }
})
