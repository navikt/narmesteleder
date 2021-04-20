package no.nav.syfo.narmesteleder.oppdatering

import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Arbeidsgiverinfo
import no.nav.syfo.narmesteleder.arbeidsforhold.service.ArbeidsgiverService
import no.nav.syfo.narmesteleder.oppdatering.kafka.NLRequestProducer
import no.nav.syfo.narmesteleder.oppdatering.kafka.NLResponseProducer
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.testutils.TestDB
import no.nav.syfo.testutils.dropData
import no.nav.syfo.testutils.lagreNarmesteleder
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@KtorExperimentalAPI
class DeaktiverNarmesteLederServiceTest : Spek({
    val nlResponseProducer = mockk<NLResponseProducer>(relaxed = true)
    val nlRequestProducer = mockk<NLRequestProducer>(relaxed = true)
    val arbeidsgiverService = mockk<ArbeidsgiverService>()
    val pdlPersonService = mockk<PdlPersonService>()
    val testDb = TestDB()
    val deaktiverNarmesteLederService = DeaktiverNarmesteLederService(nlResponseProducer, nlRequestProducer, arbeidsgiverService, pdlPersonService, testDb)

    val sykmeldtFnr = "12345678910"
    val lederFnr = "01987654321"

    beforeEachTest {
        clearMocks(arbeidsgiverService, nlRequestProducer, nlResponseProducer)
        coEvery { pdlPersonService.getPersonnavn(any(), any()) } returns mapOf(Pair(sykmeldtFnr, Navn("Fornavn", null, "Etternavn")))
        coEvery { arbeidsgiverService.getArbeidsgivere(any(), any()) } returns emptyList()
    }
    afterEachTest {
        testDb.connection.dropData()
    }
    afterGroup {
        testDb.stop()
    }

    describe("DeaktiverNarmesteLederService") {
        it("Deaktiverer NL-kobling og ber om ny NL hvis arbeidsforhold er aktivt") {
            coEvery { arbeidsgiverService.getArbeidsgivere(any(), any()) } returns listOf(Arbeidsgiverinfo("orgnummer", "juridiskOrgnummer", aktivtArbeidsforhold = true))

            runBlocking {
                deaktiverNarmesteLederService.deaktiverNarmesteLeder("orgnummer", sykmeldtFnr, "token", UUID.randomUUID())
                verify { nlResponseProducer.send(any()) }
                verify { nlRequestProducer.send(any()) }
            }
        }
        it("Deaktiverer NL-kobling uten å be om ny NL hvis arbeidsforhold ikke er aktivt") {
            coEvery { arbeidsgiverService.getArbeidsgivere(any(), any()) } returns listOf(Arbeidsgiverinfo("orgnummer", "juridiskOrgnummer", aktivtArbeidsforhold = false))

            runBlocking {
                deaktiverNarmesteLederService.deaktiverNarmesteLeder("orgnummer", sykmeldtFnr, "token", UUID.randomUUID())
                verify { nlResponseProducer.send(any()) }
                verify(exactly = 0) { nlRequestProducer.send(any()) }
            }
        }
        it("Deaktiverer NL-kobling uten å be om ny NL hvis arbeidsforhold ikke finnes i listen") {
            coEvery { arbeidsgiverService.getArbeidsgivere(any(), any()) } returns listOf(Arbeidsgiverinfo("orgnummer", "juridiskOrgnummer", aktivtArbeidsforhold = true))

            runBlocking {
                deaktiverNarmesteLederService.deaktiverNarmesteLeder("orgnummer2", sykmeldtFnr, "token", UUID.randomUUID())
                verify { nlResponseProducer.send(any()) }
                verify(exactly = 0) { nlRequestProducer.send(any()) }
            }
        }
    }

    describe("DeaktiverNarmesteLederService - deaktiver kobling for ansatt") {
        it("Deaktiverer kobling hvis finnes aktiv NL-kobling i databasen") {
            testDb.connection.lagreNarmesteleder(
                orgnummer = "orgnummer", fnr = sykmeldtFnr, fnrNl = lederFnr, arbeidsgiverForskutterer = true,
                aktivFom = OffsetDateTime.now(
                    ZoneOffset.UTC
                ).minusYears(1)
            )

            runBlocking {
                deaktiverNarmesteLederService.deaktiverNarmesteLederForAnsatt(lederFnr, "orgnummer", sykmeldtFnr, "token", UUID.randomUUID())
                verify { nlResponseProducer.send(any()) }
            }
        }
        it("Deaktiverer ikke kobling hvis NL-kobling i databasen gjelder annen ansatt") {
            testDb.connection.lagreNarmesteleder(
                orgnummer = "orgnummer", fnr = sykmeldtFnr, fnrNl = lederFnr, arbeidsgiverForskutterer = true,
                aktivFom = OffsetDateTime.now(
                    ZoneOffset.UTC
                ).minusYears(1)
            )

            runBlocking {
                deaktiverNarmesteLederService.deaktiverNarmesteLederForAnsatt(lederFnr, "orgnummer", "12312345678", "token", UUID.randomUUID())
                verify(exactly = 0) { nlResponseProducer.send(any()) }
            }
        }
        it("Deaktiverer ikke kobling hvis det ikke finnes aktive NL-koblinger i databasen") {
            runBlocking {
                deaktiverNarmesteLederService.deaktiverNarmesteLederForAnsatt(lederFnr, "orgnummer", sykmeldtFnr, "token", UUID.randomUUID())
                verify(exactly = 0) { nlResponseProducer.send(any()) }
            }
        }
    }
})
