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
import no.nav.syfo.db.getAnsatte
import no.nav.syfo.narmesteleder.NarmesteLederService
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Arbeidsgiverinfo
import no.nav.syfo.narmesteleder.arbeidsforhold.service.ArbeidsgiverService
import no.nav.syfo.narmesteleder.fnrLeder
import no.nav.syfo.narmesteleder.leder.model.Ansatt
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
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.util.UUID

class LederApiKtTest : Spek({
    val pdlPersonService = mockk<PdlPersonService>()
    val testDb = TestDB()
    val arbeidsgiverService = mockk<ArbeidsgiverService>(relaxed = true)
    val narmestelederService = NarmesteLederService(testDb, pdlPersonService, arbeidsgiverService)

    coEvery {
        arbeidsgiverService.getArbeidsgivere(any(), any(), any())
    } returns listOf(
        Arbeidsgiverinfo(
            orgnummer = "orgnummer",
            juridiskOrgnummer = "123456780",
            aktivtArbeidsforhold = true
        )
    )

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
                PdlPerson(Navn("Fornavn", null, "Etternavn"), sykmeldtFnr, "aktorid")
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

            it("Get only ACTIVE") {
                testDb.connection.lagreNarmesteleder(
                    orgnummer = "orgnummer",
                    fnr = "1",
                    fnrNl = fnrLeder,
                    arbeidsgiverForskutterer = false,
                    aktivTom = OffsetDateTime.now()
                )
                with(
                    handleRequest(HttpMethod.Get, "/arbeidsgiver/ansatte?status=active") {
                        addHeader("Cookie", "selvbetjening-idtoken=${generateJWTLoginservice(audience = "loginserviceId1", subject = fnrLeder, issuer = "issuer")}")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    val relasjoner: AnsattResponse? = response.content?.let { objectMapper.readValue(it) }
                    relasjoner shouldNotBe null
                    relasjoner!!.ansatte.size shouldBeEqualTo 1
                    relasjoner.ansatte.find { it.fnr == "1" } shouldBe null
                }
            }

            it("Get only INACTIVE") {
                testDb.connection.lagreNarmesteleder(
                    orgnummer = "orgnummer",
                    fnr = "1",
                    fnrNl = fnrLeder,
                    arbeidsgiverForskutterer = false,
                    aktivTom = OffsetDateTime.now()
                )
                with(
                    handleRequest(HttpMethod.Get, "/arbeidsgiver/ansatte?status=inactive") {
                        addHeader("Cookie", "selvbetjening-idtoken=${generateJWTLoginservice(audience = "loginserviceId1", subject = fnrLeder, issuer = "issuer")}")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    val relasjoner: AnsattResponse? = response.content?.let { objectMapper.readValue(it) }
                    relasjoner shouldNotBe null
                    relasjoner!!.ansatte.size shouldBeEqualTo 1
                    relasjoner.ansatte.find { it.fnr == "1" } shouldNotBe null
                }
            }

            it("Get ALL") {
                testDb.connection.lagreNarmesteleder(
                    orgnummer = "orgnummer",
                    fnr = "1",
                    fnrNl = fnrLeder,
                    arbeidsgiverForskutterer = false,
                    aktivTom = OffsetDateTime.now()
                )
                with(
                    handleRequest(HttpMethod.Get, "/arbeidsgiver/ansatte?status=all") {
                        addHeader("Cookie", "selvbetjening-idtoken=${generateJWTLoginservice(audience = "loginserviceId1", subject = fnrLeder, issuer = "issuer")}")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    val relasjoner: AnsattResponse? = response.content?.let { objectMapper.readValue(it) }
                    relasjoner shouldNotBe null
                    relasjoner!!.ansatte.size shouldBeEqualTo 2
                    relasjoner.ansatte.find { it.fnr == "1" } shouldNotBe null
                }
                with(
                    handleRequest(HttpMethod.Get, "/arbeidsgiver/ansatte") {
                        addHeader("Cookie", "selvbetjening-idtoken=${generateJWTLoginservice(audience = "loginserviceId1", subject = fnrLeder, issuer = "issuer")}")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    val relasjoner: AnsattResponse? = response.content?.let { objectMapper.readValue(it) }
                    relasjoner shouldNotBe null
                    relasjoner!!.ansatte.size shouldBeEqualTo 2
                    relasjoner.ansatte.find { it.fnr == "1" } shouldNotBe null
                }
            }

            it("Get single nl-relasjon") {
                val r = testDb.getAnsatte(fnrLeder)
                val nlId = r.first().narmesteLederId
                with(
                    handleRequest(HttpMethod.Get, "/arbeidsgiver/ansatt/$nlId") {
                        addHeader("Cookie", "selvbetjening-idtoken=${generateJWTLoginservice(audience = "loginserviceId1", subject = fnrLeder, issuer = "issuer")}")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    val relasjon: Ansatt? = response.content?.let { objectMapper.readValue(it) }
                    relasjon shouldNotBe null
                    relasjon!!.narmestelederId shouldBeEqualTo nlId
                    relasjon!!.fnr shouldBeEqualTo sykmeldtFnr
                    relasjon!!.orgnummer shouldBeEqualTo "orgnummer"
                }
            }

            it("Get single nl-relasjon retursn 404 when wrong nlId") {
                with(
                    handleRequest(HttpMethod.Get, "/arbeidsgiver/ansatt/123") {
                        addHeader("Cookie", "selvbetjening-idtoken=${generateJWTLoginservice(audience = "loginserviceId1", subject = fnrLeder, issuer = "issuer")}")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.NotFound
                }
            }

            it("Get single nl-relasjon return 404 when nldi not found ") {
                with(
                    handleRequest(HttpMethod.Get, "/arbeidsgiver/ansatt/${UUID.randomUUID()}") {
                        addHeader("Cookie", "selvbetjening-idtoken=${generateJWTLoginservice(audience = "loginserviceId1", subject = fnrLeder, issuer = "issuer")}")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.NotFound
                }
            }

            it("Get single nl-relasjon retursn 404 when wrong fnr on nlId") {
                val r = testDb.getAnsatte(fnrLeder)
                val nlId = r.first().narmesteLederId
                with(
                    handleRequest(HttpMethod.Get, "/arbeidsgiver/ansatt/$nlId") {
                        addHeader("Cookie", "selvbetjening-idtoken=${generateJWTLoginservice(audience = "loginserviceId1", subject = sykmeldtFnr, issuer = "issuer")}")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.NotFound
                }
            }
        }
    }
})
