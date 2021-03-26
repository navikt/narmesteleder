package no.nav.syfo.pdl.service

import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockkClass
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.OidcToken
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.client.model.HentPersonBolk
import no.nav.syfo.pdl.client.model.Person
import no.nav.syfo.pdl.client.model.ResponseData
import no.nav.syfo.pdl.model.Navn
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertFailsWith

@KtorExperimentalAPI
object PdlPersonServiceTest : Spek({
    val pdlClient = mockkClass(PdlClient::class)
    val stsOidcClient = mockkClass(StsOidcClient::class)
    val pdlPersonService = PdlPersonService(pdlClient, stsOidcClient)

    val callId = "callid"
    val aktorIdLeder1 = "123"
    val aktorIdLeder2 = "456"

    beforeEachTest {
        clearMocks(stsOidcClient)
        coEvery { stsOidcClient.oidcToken() } returns OidcToken("Token", "JWT", 1L)
    }

    describe("Test av PdlPersonService") {
        it("Henter navn for to ledere") {
            coEvery { pdlClient.getPersoner(any(), any()) } returns GetPersonResponse(
                ResponseData(
                    hentPersonBolk = listOf(
                        HentPersonBolk(aktorIdLeder1, Person(listOf(no.nav.syfo.pdl.client.model.Navn("fornavn", null, "etternavn"))), "ok"),
                        HentPersonBolk(aktorIdLeder2, Person(listOf(no.nav.syfo.pdl.client.model.Navn("fornavn2", "mellomnavn", "etternavn2"))), "ok")
                    )
                ),
                errors = null
            )

            runBlocking {
                val personer = pdlPersonService.getPersonnavn(listOf(aktorIdLeder1, aktorIdLeder2), callId)

                personer[aktorIdLeder1] shouldBeEqualTo Navn("fornavn", null, "etternavn")
                personer[aktorIdLeder2] shouldBeEqualTo Navn("fornavn2", "mellomnavn", "etternavn2")
            }
        }
        it("Navn er null hvis aktør ikke finnes i PDL") {
            coEvery { pdlClient.getPersoner(any(), any()) } returns GetPersonResponse(
                ResponseData(
                    hentPersonBolk = listOf(
                        HentPersonBolk(aktorIdLeder1, null, "not_found"),
                        HentPersonBolk(aktorIdLeder2, Person(listOf(no.nav.syfo.pdl.client.model.Navn("fornavn", null, "etternavn"))), "ok")
                    )
                ),
                errors = null
            )

            runBlocking {
                val personer = pdlPersonService.getPersonnavn(listOf(aktorIdLeder1, aktorIdLeder2), callId)

                personer[aktorIdLeder1] shouldBeEqualTo null
                personer[aktorIdLeder2] shouldBeEqualTo Navn("fornavn", null, "etternavn")
            }
        }
        it("Skal feile når ingen personer finnes") {
            coEvery { pdlClient.getPersoner(any(), any()) } returns GetPersonResponse(ResponseData(hentPersonBolk = emptyList()), errors = null)

            assertFailsWith<IllegalStateException> {
                runBlocking {
                    pdlPersonService.getPersonnavn(listOf("fnrPasient", "fnrLege"), callId)
                }
            }
        }
    }
})
