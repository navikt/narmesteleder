package no.nav.syfo.identendring

import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.db.finnAktiveNarmestelederkoblinger
import no.nav.syfo.db.finnAlleNarmesteledereForSykmeldt
import no.nav.syfo.identendring.model.Ident
import no.nav.syfo.identendring.model.IdentType
import no.nav.syfo.narmesteleder.oppdatering.OppdaterNarmesteLederService
import no.nav.syfo.narmesteleder.oppdatering.kafka.NarmesteLederLeesahProducer
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.testutils.TestDB
import no.nav.syfo.testutils.dropData
import no.nav.syfo.testutils.lagreNarmesteleder
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.time.ZoneOffset

class IdentendringServiceTest : Spek({
    val pdlPersonService = mockk<PdlPersonService>(relaxed = true)
    val narmesteLederLeesahProducer = mockk<NarmesteLederLeesahProducer>(relaxed = true)
    val testDb = TestDB()
    val oppdaterNarmesteLederService = OppdaterNarmesteLederService(pdlPersonService, testDb, narmesteLederLeesahProducer)
    val identendringService = IdentendringService(testDb, oppdaterNarmesteLederService, pdlPersonService)
    val sykmeldtFnr = "12345678910"
    val nyttFnrSykmeldt = "316497852"
    val fnrLeder = "10987654321"
    val nyttFnrLeder = "89764521"

    beforeEachTest {
        clearMocks(pdlPersonService, narmesteLederLeesahProducer)
        coEvery { pdlPersonService.getPersoner(any(), any()) } returns mapOf(
            Pair(fnrLeder, PdlPerson(Navn("Leder", null, "Ledersen"), fnrLeder, "aktorid")),
            Pair(nyttFnrLeder, PdlPerson(Navn("Leder", null, "Ledersen"), fnrLeder, "aktorid")),
            Pair(sykmeldtFnr, PdlPerson(Navn("Syk", null, "Sykesen"), sykmeldtFnr, "aktorid2")),
            Pair(nyttFnrSykmeldt, PdlPerson(Navn("Syk", null, "Sykesen"), sykmeldtFnr, "aktorid2"))
        )
    }
    afterEachTest {
        testDb.connection.dropData()
    }
    afterGroup {
        testDb.stop()
    }

    describe("IdentendringService") {
        it("Endrer ingenting hvis det ikke er endring i fnr") {
            val identListeUtenEndringIFnr = listOf(
                Ident(idnummer = "1234", gjeldende = true, type = IdentType.FOLKEREGISTERIDENT),
                Ident(idnummer = "1111", gjeldende = true, type = IdentType.AKTORID),
                Ident(idnummer = "2222", gjeldende = false, type = IdentType.AKTORID)
            )
            runBlocking {
                identendringService.oppdaterIdent(identListeUtenEndringIFnr)

                coVerify(exactly = 0) { pdlPersonService.getPersoner(any(), any()) }
                coVerify(exactly = 0) { narmesteLederLeesahProducer.send(any()) }
            }
        }
        it("Endrer ingenting hvis det ikke finnes NL-koblinger på gammelt fnr") {
            val identListeUtenEndringIFnr = listOf(
                Ident(idnummer = "1234", gjeldende = true, type = IdentType.FOLKEREGISTERIDENT),
                Ident(idnummer = "1111", gjeldende = true, type = IdentType.AKTORID),
                Ident(idnummer = "2222", gjeldende = false, type = IdentType.FOLKEREGISTERIDENT)
            )
            runBlocking {
                identendringService.oppdaterIdent(identListeUtenEndringIFnr)

                coVerify(exactly = 0) { pdlPersonService.getPersoner(any(), any()) }
                coVerify(exactly = 0) { narmesteLederLeesahProducer.send(any()) }
            }
        }
        it("Oppdaterer alle aktive NL-koblinger hvis leder har byttet fnr") {
            testDb.connection.lagreNarmesteleder(
                orgnummer = "orgnummer", fnr = sykmeldtFnr, fnrNl = fnrLeder, arbeidsgiverForskutterer = true,
                aktivFom = OffsetDateTime.now(
                    ZoneOffset.UTC
                ).minusYears(1)
            )
            testDb.connection.lagreNarmesteleder(
                orgnummer = "orgnummer", fnr = "123456", fnrNl = fnrLeder, arbeidsgiverForskutterer = true,
                aktivFom = OffsetDateTime.now(
                    ZoneOffset.UTC
                ).minusYears(2),
                aktivTom = OffsetDateTime.now(
                    ZoneOffset.UTC
                ).minusYears(1)
            )

            val aktorId = "1111"
            val identListe = listOf(
                Ident(idnummer = nyttFnrLeder, gjeldende = true, type = IdentType.FOLKEREGISTERIDENT),
                Ident(idnummer = aktorId, gjeldende = true, type = IdentType.AKTORID),
                Ident(idnummer = fnrLeder, gjeldende = false, type = IdentType.FOLKEREGISTERIDENT)

            )
            coEvery { pdlPersonService.erIdentAktiv(any()) } returns true

            runBlocking {

                identendringService.oppdaterIdent(identListe)

                coVerify { pdlPersonService.getPersoner(any(), any()) }
                coVerify(exactly = 2) { narmesteLederLeesahProducer.send(any()) }

                testDb.finnAktiveNarmestelederkoblinger(fnrLeder).size shouldBeEqualTo 0

                val nlListe = testDb.finnAlleNarmesteledereForSykmeldt(sykmeldtFnr)
                nlListe.size shouldBeEqualTo 2
                val nyNl = nlListe.find { it.aktivTom == null }
                nyNl?.narmesteLederFnr shouldBeEqualTo nyttFnrLeder
                nyNl?.aktivFom shouldBeEqualTo OffsetDateTime.now(ZoneOffset.UTC).minusYears(1).toLocalDate()
                nyNl?.arbeidsgiverForskutterer shouldBeEqualTo true
                nyNl?.orgnummer shouldBeEqualTo "orgnummer"
                nyNl?.narmesteLederEpost shouldBeEqualTo "epost@nav.no"
                nyNl?.narmesteLederTelefonnummer shouldBeEqualTo "90909090"
                val gammelNl = nlListe.find { it.aktivTom != null }
                gammelNl?.narmesteLederFnr shouldBeEqualTo fnrLeder

                val uendretNL = testDb.finnAlleNarmesteledereForSykmeldt("123456")
                uendretNL.size shouldBeEqualTo 1
                uendretNL[0].narmesteLederFnr shouldBeEqualTo fnrLeder
                uendretNL[0].aktivFom shouldBeEqualTo OffsetDateTime.now(ZoneOffset.UTC).minusYears(2).toLocalDate()
                uendretNL[0].aktivTom shouldBeEqualTo OffsetDateTime.now(ZoneOffset.UTC).minusYears(1).toLocalDate()
                uendretNL[0].orgnummer shouldBeEqualTo "orgnummer"
            }
        }
        it("Oppretter aktive NL-koblinger med nytt fnr og bryter aktive koblinger med gammelt fnr hvis ansatt bytter fnr") {
            testDb.connection.lagreNarmesteleder(
                orgnummer = "orgnummer", fnr = sykmeldtFnr, fnrNl = fnrLeder, arbeidsgiverForskutterer = true,
                aktivFom = OffsetDateTime.now(
                    ZoneOffset.UTC
                ).minusYears(1)
            )
            testDb.connection.lagreNarmesteleder(
                orgnummer = "orgnummer2", fnr = sykmeldtFnr, fnrNl = "123456", arbeidsgiverForskutterer = true,
                aktivFom = OffsetDateTime.now(
                    ZoneOffset.UTC
                ).minusYears(2),
                aktivTom = OffsetDateTime.now(
                    ZoneOffset.UTC
                ).minusYears(1)
            )

            coEvery { pdlPersonService.erIdentAktiv(nyttFnrSykmeldt) } returns true
            coEvery { pdlPersonService.erIdentAktiv("1111") } returns true

            val identListe = listOf(
                Ident(idnummer = nyttFnrSykmeldt, gjeldende = true, type = IdentType.FOLKEREGISTERIDENT),
                Ident(idnummer = "1111", gjeldende = true, type = IdentType.AKTORID),
                Ident(idnummer = sykmeldtFnr, gjeldende = false, type = IdentType.FOLKEREGISTERIDENT)
            )
            runBlocking {
                identendringService.oppdaterIdent(identListe)

                coVerify { pdlPersonService.getPersoner(any(), any()) }
                coVerify(exactly = 2) { narmesteLederLeesahProducer.send(any()) }

                val nlListeGammeltFnr = testDb.finnAlleNarmesteledereForSykmeldt(sykmeldtFnr)
                nlListeGammeltFnr.size shouldBeEqualTo 2
                nlListeGammeltFnr.find { it.aktivTom == null } shouldBeEqualTo null
                nlListeGammeltFnr.find { it.orgnummer == "orgnummer" }?.narmesteLederFnr shouldBeEqualTo fnrLeder
                nlListeGammeltFnr.find { it.orgnummer == "orgnummer2" }?.narmesteLederFnr shouldBeEqualTo "123456"

                val nlListeNyttFnr = testDb.finnAlleNarmesteledereForSykmeldt(nyttFnrSykmeldt)
                nlListeNyttFnr.size shouldBeEqualTo 1
                nlListeNyttFnr[0].narmesteLederFnr shouldBeEqualTo fnrLeder
                nlListeNyttFnr[0].aktivFom shouldBeEqualTo OffsetDateTime.now(ZoneOffset.UTC).minusYears(1).toLocalDate()
                nlListeNyttFnr[0].aktivTom shouldBeEqualTo null
                nlListeNyttFnr[0].arbeidsgiverForskutterer shouldBeEqualTo true
                nlListeNyttFnr[0].orgnummer shouldBeEqualTo "orgnummer"
                nlListeNyttFnr[0].narmesteLederEpost shouldBeEqualTo "epost@nav.no"
                nlListeNyttFnr[0].narmesteLederTelefonnummer shouldBeEqualTo "90909090"
            }
        }
    }
})
