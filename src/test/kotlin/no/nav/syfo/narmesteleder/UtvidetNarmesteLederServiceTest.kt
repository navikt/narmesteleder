package no.nav.syfo.narmesteleder

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockkClass
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.DelicateCoroutinesApi
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.testutils.TestDB
import no.nav.syfo.testutils.dropData
import no.nav.syfo.testutils.lagreNarmesteleder
import org.amshove.kluent.shouldBeEqualTo

@DelicateCoroutinesApi
class UtvidetNarmesteLederServiceTest :
    FunSpec({
        val testDb = TestDB()
        val pdlPersonService = mockkClass(PdlPersonService::class)
        val utvidetNarmesteLederService = NarmesteLederService(testDb, pdlPersonService)

        val callId = "callid"
        val fnr = "fnr"
        val fnrLeder1 = "123"
        val fnrLeder2 = "456"

        beforeTest { clearMocks(pdlPersonService) }

        afterTest { testDb.connection.dropData() }

        afterSpec { testDb.stop() }

        context("UtvidetNarmesteLederService") {
            test("Setter riktig navn på ledere") {
                testDb.connection.lagreNarmesteleder(
                    orgnummer = "orgnummer",
                    fnr = fnr,
                    fnrNl = fnrLeder1,
                    arbeidsgiverForskutterer = true
                )
                testDb.connection.lagreNarmesteleder(
                    orgnummer = "orgnummer2",
                    fnr = fnr,
                    fnrNl = fnrLeder2,
                    arbeidsgiverForskutterer = true,
                    aktivTom =
                        OffsetDateTime.now(
                                ZoneOffset.UTC,
                            )
                            .minusDays(2),
                )
                coEvery { pdlPersonService.getPersoner(any(), any()) } returns
                    mapOf(
                        Pair(
                            fnrLeder1,
                            PdlPerson(
                                Navn("FORNAVN EKSTRANAVN", null, "ETTERNAVN"),
                                fnrLeder1,
                                "aktorid1"
                            )
                        ),
                        Pair(
                            fnrLeder2,
                            PdlPerson(
                                Navn("FORNAVN2", "MELLOMNAVN", "BINDESTREK-ETTERNAVN"),
                                fnrLeder2,
                                "aktorid2"
                            )
                        ),
                    )

                val narmesteLedereMedNavn =
                    utvidetNarmesteLederService.hentNarmesteledereMedNavn(fnr, callId)

                narmesteLedereMedNavn.size shouldBeEqualTo 2
                val nl1 = narmesteLedereMedNavn.find { it.narmesteLederFnr == fnrLeder1 }
                nl1?.navn shouldBeEqualTo "Fornavn Ekstranavn Etternavn"
                val nl2 = narmesteLedereMedNavn.find { it.narmesteLederFnr == fnrLeder2 }
                nl2?.navn shouldBeEqualTo "Fornavn2 Mellomnavn Bindestrek-Etternavn"
            }
            test("Setter null som navn hvis navn mangler i PDL (feiler ikke)") {
                testDb.connection.lagreNarmesteleder(
                    orgnummer = "orgnummer",
                    fnr = fnr,
                    fnrNl = fnrLeder1,
                    arbeidsgiverForskutterer = true
                )
                testDb.connection.lagreNarmesteleder(
                    orgnummer = "orgnummer2",
                    fnr = fnr,
                    fnrNl = fnrLeder2,
                    arbeidsgiverForskutterer = true,
                    aktivTom =
                        OffsetDateTime.now(
                                ZoneOffset.UTC,
                            )
                            .minusDays(2),
                )
                coEvery { pdlPersonService.getPersoner(any(), any()) } returns
                    mapOf(
                        Pair(
                            fnrLeder1,
                            PdlPerson(Navn("FORNAVN", null, "ETTERNAVN"), fnrLeder1, "aktorid1")
                        ),
                        Pair(fnrLeder2, null),
                    )

                val narmesteLedereMedNavn =
                    utvidetNarmesteLederService.hentNarmesteledereMedNavn(fnr, callId)

                narmesteLedereMedNavn.size shouldBeEqualTo 2
                val nl1 = narmesteLedereMedNavn.find { it.narmesteLederFnr == fnrLeder1 }
                nl1?.navn shouldBeEqualTo "Fornavn Etternavn"
                val nl2 = narmesteLedereMedNavn.find { it.narmesteLederFnr == fnrLeder2 }
                nl2?.navn shouldBeEqualTo null
            }
            test("Returnerer tom liste hvis bruker ikke har noen nærmeste ledere") {
                val narmesteLedereMedNavn =
                    utvidetNarmesteLederService.hentNarmesteledereMedNavn(fnr, callId)

                narmesteLedereMedNavn.size shouldBeEqualTo 0
            }
        }
    })
