package no.nav.syfo.pdl.service

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkClass
import kotlin.test.assertFailsWith
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.client.AccessTokenClientV2
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.client.model.HentIdenterBolk
import no.nav.syfo.pdl.client.model.HentPersonBolk
import no.nav.syfo.pdl.client.model.PdlIdent
import no.nav.syfo.pdl.client.model.Person
import no.nav.syfo.pdl.client.model.ResponseData
import no.nav.syfo.pdl.error.InactiveIdentException
import no.nav.syfo.pdl.error.PersonNotFoundException
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import org.amshove.kluent.shouldBeEqualTo

@DelicateCoroutinesApi
class PdlPersonServiceTest :
    FunSpec({
        val pdlClient = mockkClass(PdlClient::class)
        val accessTokenClientV2 = mockkClass(AccessTokenClientV2::class)
        val pdlPersonService = PdlPersonService(pdlClient, accessTokenClientV2, "scope")

        val callId = "callid"
        val fnrLeder1 = "12345678910"
        val fnrLeder2 = "01987654321"
        val aktorIdLeder1 = "123"
        val aktorIdLeder2 = "456"

        beforeTest {
            clearMocks(accessTokenClientV2, pdlClient)
            coEvery { accessTokenClientV2.getAccessTokenV2(any()) } returns "token"
        }

        context("Test av PdlPersonService") {
            test("Henter navn og aktørid for to ledere") {
                coEvery { pdlClient.getPersoner(any(), any()) } returns
                    GetPersonResponse(
                        ResponseData(
                            hentPersonBolk =
                                listOf(
                                    HentPersonBolk(
                                        fnrLeder1,
                                        Person(
                                            listOf(
                                                no.nav.syfo.pdl.client.model.Navn(
                                                    "fornavn",
                                                    null,
                                                    "etternavn"
                                                )
                                            )
                                        ),
                                        "ok"
                                    ),
                                    HentPersonBolk(
                                        fnrLeder2,
                                        Person(
                                            listOf(
                                                no.nav.syfo.pdl.client.model.Navn(
                                                    "fornavn2",
                                                    "mellomnavn",
                                                    "etternavn2"
                                                )
                                            )
                                        ),
                                        "ok"
                                    ),
                                ),
                            hentIdenterBolk =
                                listOf(
                                    HentIdenterBolk(
                                        fnrLeder1,
                                        listOf(
                                            PdlIdent(fnrLeder1, "FOLKEREGISTERIDENT"),
                                            PdlIdent(aktorIdLeder1, PdlPersonService.AKTORID)
                                        ),
                                        "ok"
                                    ),
                                    HentIdenterBolk(
                                        fnrLeder2,
                                        listOf(
                                            PdlIdent(fnrLeder2, "FOLKEREGISTERIDENT"),
                                            PdlIdent(aktorIdLeder2, PdlPersonService.AKTORID)
                                        ),
                                        "ok"
                                    ),
                                ),
                        ),
                        errors = null,
                    )

                val personer = pdlPersonService.getPersoner(listOf(fnrLeder1, fnrLeder2), callId)

                personer[fnrLeder1] shouldBeEqualTo
                    PdlPerson(Navn("fornavn", null, "etternavn"), fnrLeder1, aktorIdLeder1)
                personer[fnrLeder2] shouldBeEqualTo
                    PdlPerson(
                        Navn("fornavn2", "mellomnavn", "etternavn2"),
                        fnrLeder2,
                        aktorIdLeder2
                    )
            }
            test("Person er null hvis navn ikke finnes i PDL") {
                coEvery { pdlClient.getPersoner(any(), any()) } returns
                    GetPersonResponse(
                        ResponseData(
                            hentPersonBolk =
                                listOf(
                                    HentPersonBolk(fnrLeder1, null, "not_found"),
                                    HentPersonBolk(
                                        fnrLeder2,
                                        Person(
                                            listOf(
                                                no.nav.syfo.pdl.client.model.Navn(
                                                    "fornavn",
                                                    null,
                                                    "etternavn"
                                                )
                                            )
                                        ),
                                        "ok"
                                    ),
                                ),
                            hentIdenterBolk =
                                listOf(
                                    HentIdenterBolk(fnrLeder1, null, "not_found"),
                                    HentIdenterBolk(
                                        fnrLeder2,
                                        listOf(
                                            PdlIdent(fnrLeder2, "FOLKEREGISTERIDENT"),
                                            PdlIdent(aktorIdLeder2, PdlPersonService.AKTORID)
                                        ),
                                        "ok"
                                    ),
                                ),
                        ),
                        errors = null,
                    )

                val personer = pdlPersonService.getPersoner(listOf(fnrLeder1, fnrLeder2), callId)

                personer[fnrLeder1] shouldBeEqualTo null
                personer[fnrLeder2] shouldBeEqualTo
                    PdlPerson(Navn("fornavn", null, "etternavn"), fnrLeder2, aktorIdLeder2)
            }
            test("Skal feile når ingen personer finnes") {
                coEvery { pdlClient.getPersoner(any(), any()) } returns
                    GetPersonResponse(
                        ResponseData(hentPersonBolk = emptyList(), hentIdenterBolk = emptyList()),
                        errors = null
                    )

                assertFailsWith<IllegalStateException> {
                    runBlocking {
                        pdlPersonService.getPersoner(listOf(fnrLeder1, fnrLeder2), callId)
                    }
                }
            }
            test("Skal hente 1000 narmesteldere") {
                val total = 1001
                val fnrs = (0 until total).map { it.toString() }
                coEvery { pdlClient.getPersoner(any(), any()) } answers
                    {
                        val fnrList = args[0] as List<String>
                        GetPersonResponse(
                            data =
                                ResponseData(
                                    hentPersonBolk =
                                        fnrList.map {
                                            HentPersonBolk(
                                                it,
                                                Person(
                                                    navn =
                                                        listOf(
                                                            no.nav.syfo.pdl.client.model.Navn(
                                                                "Fornavn",
                                                                "Mellomnanv",
                                                                "Etternavn",
                                                            ),
                                                        ),
                                                ),
                                                code = "ok",
                                            )
                                        },
                                    hentIdenterBolk =
                                        fnrs.map {
                                            HentIdenterBolk(
                                                it,
                                                listOf(
                                                    PdlIdent(it, "FOLKEREGISTERIDENT"),
                                                    PdlIdent(it, PdlPersonService.AKTORID)
                                                ),
                                                code = "ok",
                                            )
                                        },
                                ),
                            emptyList(),
                        )
                    }

                val personer = pdlPersonService.getPersoner(fnrs = fnrs, "callid")
                coVerify(exactly = 11) { pdlClient.getPersoner(any(), any()) }

                personer.size shouldBeEqualTo 1001
            }

            test("Skal feile når person ikke finnes") {
                coEvery { pdlClient.getPersoner(any(), any()) } returns
                    GetPersonResponse(
                        ResponseData(hentPersonBolk = emptyList(), hentIdenterBolk = emptyList()),
                        errors = null,
                    )

                val exception =
                    assertFailsWith<PersonNotFoundException> {
                        runBlocking { pdlPersonService.erIdentAktiv("123") }
                    }
                exception.message shouldBeEqualTo "Fant ikke person i PDL"
            }

            test("Skal feile når ident ikke er aktiv") {
                coEvery { pdlClient.getPersoner(any(), any()) } returns
                    GetPersonResponse(
                        data =
                            ResponseData(
                                hentPersonBolk =
                                    listOf(
                                        HentPersonBolk(
                                            "123",
                                            Person(
                                                navn =
                                                    listOf(
                                                        no.nav.syfo.pdl.client.model.Navn(
                                                            "Fornavn",
                                                            "Mellomnanv",
                                                            "Etternavn",
                                                        ),
                                                    ),
                                            ),
                                            code = "ok",
                                        ),
                                    ),
                                hentIdenterBolk =
                                    listOf(
                                        HentIdenterBolk(
                                            "123",
                                            listOf(
                                                PdlIdent("123", "FOLKEREGISTERIDENT"),
                                                PdlIdent("234", PdlPersonService.AKTORID)
                                            ),
                                            code = "ok",
                                        ),
                                    ),
                            ),
                        emptyList(),
                    )

                val exception =
                    assertFailsWith<InactiveIdentException> {
                        runBlocking { pdlPersonService.erIdentAktiv("999") }
                    }
                exception.message shouldBeEqualTo "PDL svarer men ident er ikke aktiv"
            }
        }
    })
