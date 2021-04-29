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
import no.nav.syfo.pdl.client.model.HentIdenterBolk
import no.nav.syfo.pdl.client.model.HentPersonBolk
import no.nav.syfo.pdl.client.model.PdlIdent
import no.nav.syfo.pdl.client.model.Person
import no.nav.syfo.pdl.client.model.ResponseData
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
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
    val fnrLeder1 = "12345678910"
    val fnrLeder2 = "01987654321"
    val aktorIdLeder1 = "123"
    val aktorIdLeder2 = "456"

    beforeEachTest {
        clearMocks(stsOidcClient)
        coEvery { stsOidcClient.oidcToken() } returns OidcToken("Token", "JWT", 1L)
    }

    describe("Test av PdlPersonService") {
        it("Henter navn og aktørid for to ledere") {
            coEvery { pdlClient.getPersoner(any(), any()) } returns GetPersonResponse(
                ResponseData(
                    hentPersonBolk = listOf(
                        HentPersonBolk(fnrLeder1, Person(listOf(no.nav.syfo.pdl.client.model.Navn("fornavn", null, "etternavn"))), "ok"),
                        HentPersonBolk(fnrLeder2, Person(listOf(no.nav.syfo.pdl.client.model.Navn("fornavn2", "mellomnavn", "etternavn2"))), "ok")
                    ),
                    hentIdenterBolk = listOf(
                        HentIdenterBolk(fnrLeder1, listOf(PdlIdent(fnrLeder1, "FOLKEREGISTERIDENT"), PdlIdent(aktorIdLeder1, PdlPersonService.AKTORID)), "ok"),
                        HentIdenterBolk(fnrLeder2, listOf(PdlIdent(fnrLeder2, "FOLKEREGISTERIDENT"), PdlIdent(aktorIdLeder2, PdlPersonService.AKTORID)), "ok")
                    )
                ),
                errors = null
            )

            runBlocking {
                val personer = pdlPersonService.getPersoner(listOf(fnrLeder1, fnrLeder2), callId)

                personer[fnrLeder1] shouldBeEqualTo PdlPerson(Navn("fornavn", null, "etternavn"), fnrLeder1, aktorIdLeder1)
                personer[fnrLeder2] shouldBeEqualTo PdlPerson(Navn("fornavn2", "mellomnavn", "etternavn2"), fnrLeder2, aktorIdLeder2)
            }
        }
        it("Person er null hvis navn ikke finnes i PDL") {
            coEvery { pdlClient.getPersoner(any(), any()) } returns GetPersonResponse(
                ResponseData(
                    hentPersonBolk = listOf(
                        HentPersonBolk(fnrLeder1, null, "not_found"),
                        HentPersonBolk(fnrLeder2, Person(listOf(no.nav.syfo.pdl.client.model.Navn("fornavn", null, "etternavn"))), "ok")
                    ),
                    hentIdenterBolk = listOf(
                        HentIdenterBolk(fnrLeder1, null, "not_found"),
                        HentIdenterBolk(fnrLeder2, listOf(PdlIdent(fnrLeder2, "FOLKEREGISTERIDENT"), PdlIdent(aktorIdLeder2, PdlPersonService.AKTORID)), "ok")
                    )
                ),
                errors = null
            )

            runBlocking {
                val personer = pdlPersonService.getPersoner(listOf(fnrLeder1, fnrLeder2), callId)

                personer[fnrLeder1] shouldBeEqualTo null
                personer[fnrLeder2] shouldBeEqualTo PdlPerson(Navn("fornavn", null, "etternavn"), fnrLeder2, aktorIdLeder2)
            }
        }
        it("Skal feile når ingen personer finnes") {
            coEvery { pdlClient.getPersoner(any(), any()) } returns GetPersonResponse(ResponseData(hentPersonBolk = emptyList(), hentIdenterBolk = emptyList()), errors = null)

            assertFailsWith<IllegalStateException> {
                runBlocking {
                    pdlPersonService.getPersoner(listOf(fnrLeder1, fnrLeder2), callId)
                }
            }
        }
    }
})
