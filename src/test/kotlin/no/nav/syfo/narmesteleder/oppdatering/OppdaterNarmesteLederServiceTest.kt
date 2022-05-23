package no.nav.syfo.narmesteleder.oppdatering

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.runBlocking
import no.nav.syfo.db.finnAlleNarmesteledereForSykmeldt
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Arbeidsgiverinfo
import no.nav.syfo.narmesteleder.arbeidsforhold.service.ArbeidsgiverService
import no.nav.syfo.narmesteleder.oppdatering.kafka.NLRequestProducer
import no.nav.syfo.narmesteleder.oppdatering.kafka.NarmesteLederLeesahProducer
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.DEAKTIVERT_ARBEIDSTAKER
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.DEAKTIVERT_ARBEIDSTAKER_INNSENDT_SYKMELDING
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.DEAKTIVERT_LEDER
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.DEAKTIVERT_NY_LEDER
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.KafkaMetadata
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.NY_LEDER
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.NlResponseKafkaMessage
import no.nav.syfo.narmesteleder.oppdatering.model.Leder
import no.nav.syfo.narmesteleder.oppdatering.model.NlAvbrutt
import no.nav.syfo.narmesteleder.oppdatering.model.NlResponse
import no.nav.syfo.narmesteleder.oppdatering.model.Sykmeldt
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.testutils.TestDB
import no.nav.syfo.testutils.dropData
import no.nav.syfo.testutils.lagreNarmesteleder
import org.amshove.kluent.shouldBeEqualTo
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertFailsWith

@DelicateCoroutinesApi
class OppdaterNarmesteLederServiceTest : FunSpec({
    val pdlPersonService = mockk<PdlPersonService>()
    val narmesteLederLeesahProducer = mockk<NarmesteLederLeesahProducer>(relaxed = true)
    val nlRequestProducer = mockk<NLRequestProducer>(relaxed = true)
    val arbeidsgiverService = mockk<ArbeidsgiverService>()
    val testDb = TestDB()
    val oppdaterNarmesteLederService = OppdaterNarmesteLederService(pdlPersonService, arbeidsgiverService, testDb, narmesteLederLeesahProducer, nlRequestProducer)
    val sykmeldtFnr = "fnr"
    val fnrLeder = "123"
    val fnrLeder2 = "123456"
    val timestamp = OffsetDateTime.now(ZoneOffset.UTC)

    beforeTest {
        clearMocks(pdlPersonService, narmesteLederLeesahProducer, arbeidsgiverService, nlRequestProducer)
        coEvery { pdlPersonService.getPersoner(any(), any()) } returns mapOf(
            Pair(fnrLeder, PdlPerson(Navn("Leder", null, "Ledersen"), fnrLeder, "aktorid")),
            Pair(fnrLeder2, PdlPerson(Navn("Leder", null, "Ledersen"), fnrLeder2, "aktorid2")),
            Pair(sykmeldtFnr, PdlPerson(Navn("Syk", null, "Sykesen"), sykmeldtFnr, "aktorid3"))
        )
    }
    afterTest {
        testDb.connection.dropData()
    }
    afterSpec {
        testDb.stop()
    }

    context("OppdaterNarmesteLederService") {
        test("Oppretter ny nærmeste leder hvis ingen finnes fra før") {
            val nlResponseKafkaMessage = NlResponseKafkaMessage(
                kafkaMetadata = KafkaMetadata(timestamp, "syfonlaltinn"),
                nlResponse = NlResponse(
                    "orgnummer", utbetalesLonn = true, Leder(fnrLeder, "90909090", "epost@nav.no", "Leder", "Ledersen"),
                    Sykmeldt(sykmeldtFnr, "Syk Sykesen")
                ),
                nlAvbrutt = null
            )

            oppdaterNarmesteLederService.handterMottattNarmesteLederOppdatering(nlResponseKafkaMessage, 0, 0)

            val nlListe = testDb.finnAlleNarmesteledereForSykmeldt(sykmeldtFnr)
            nlListe.size shouldBeEqualTo 1
            nlListe[0].fnr shouldBeEqualTo sykmeldtFnr
            nlListe[0].narmesteLederFnr shouldBeEqualTo fnrLeder
            nlListe[0].aktivFom shouldBeEqualTo timestamp.toLocalDate()
            nlListe[0].aktivTom shouldBeEqualTo null
            nlListe[0].arbeidsgiverForskutterer shouldBeEqualTo true
            nlListe[0].orgnummer shouldBeEqualTo "orgnummer"
            nlListe[0].narmesteLederEpost shouldBeEqualTo "epost@nav.no"
            nlListe[0].narmesteLederTelefonnummer shouldBeEqualTo "90909090"

            coVerify(exactly = 1) { narmesteLederLeesahProducer.send(any()) }
        }
        test("Oppdaterer nærmeste leder hvis finnes fra før") {
            testDb.connection.lagreNarmesteleder(orgnummer = "orgnummer", fnr = sykmeldtFnr, fnrNl = fnrLeder, arbeidsgiverForskutterer = true, aktivFom = OffsetDateTime.now(ZoneOffset.UTC).minusYears(1))
            val nlResponseKafkaMessage = NlResponseKafkaMessage(
                kafkaMetadata = KafkaMetadata(timestamp, "syfonlaltinn"),
                nlResponse = NlResponse(
                    "orgnummer", utbetalesLonn = false, Leder(fnrLeder, "90909090", "epost2@nav.no", "Leder", "Ledersen"),
                    Sykmeldt(sykmeldtFnr, "Syk Sykesen")
                ),
                nlAvbrutt = null
            )

            oppdaterNarmesteLederService.handterMottattNarmesteLederOppdatering(nlResponseKafkaMessage, 0, 0)

            val nlListe = testDb.finnAlleNarmesteledereForSykmeldt(sykmeldtFnr)
            nlListe.size shouldBeEqualTo 1
            nlListe[0].fnr shouldBeEqualTo sykmeldtFnr
            nlListe[0].narmesteLederFnr shouldBeEqualTo fnrLeder
            nlListe[0].aktivFom shouldBeEqualTo OffsetDateTime.now(ZoneOffset.UTC).minusYears(1).toLocalDate()
            nlListe[0].aktivTom shouldBeEqualTo null
            nlListe[0].arbeidsgiverForskutterer shouldBeEqualTo false
            nlListe[0].orgnummer shouldBeEqualTo "orgnummer"
            nlListe[0].narmesteLederEpost shouldBeEqualTo "epost2@nav.no"
            nlListe[0].narmesteLederTelefonnummer shouldBeEqualTo "90909090"

            coVerify(exactly = 1) { narmesteLederLeesahProducer.send(any()) }
        }
        test("Deaktiverer tidligere nærmeste leder hvis ny leder meldes inn for samme orgnummer") {
            testDb.connection.lagreNarmesteleder(orgnummer = "orgnummer", fnr = sykmeldtFnr, fnrNl = fnrLeder, arbeidsgiverForskutterer = true, aktivFom = OffsetDateTime.now(ZoneOffset.UTC).minusYears(1))
            val nlResponseKafkaMessage = NlResponseKafkaMessage(
                kafkaMetadata = KafkaMetadata(timestamp, "syfonlaltinn"),
                nlResponse = NlResponse(
                    "orgnummer", utbetalesLonn = false, Leder(fnrLeder2, "40404040", "epost2@nav.no", "Leder", "Ledersen"),
                    Sykmeldt(sykmeldtFnr, "Syk Sykesen")
                ),
                nlAvbrutt = null
            )

            oppdaterNarmesteLederService.handterMottattNarmesteLederOppdatering(nlResponseKafkaMessage, 0, 0)

            val nlListe = testDb.finnAlleNarmesteledereForSykmeldt(sykmeldtFnr)
            nlListe.size shouldBeEqualTo 2
            val gammelNl = nlListe.find { it.narmesteLederFnr == fnrLeder }
            val nyNl = nlListe.find { it.narmesteLederFnr == fnrLeder2 }
            gammelNl?.aktivFom shouldBeEqualTo OffsetDateTime.now(ZoneOffset.UTC).minusYears(1).toLocalDate()
            gammelNl?.aktivTom shouldBeEqualTo OffsetDateTime.now(ZoneOffset.UTC).toLocalDate()
            gammelNl?.orgnummer shouldBeEqualTo "orgnummer"
            nyNl?.aktivFom shouldBeEqualTo timestamp.toLocalDate()
            nyNl?.aktivTom shouldBeEqualTo null
            nyNl?.arbeidsgiverForskutterer shouldBeEqualTo false
            nyNl?.orgnummer shouldBeEqualTo "orgnummer"
            nyNl?.narmesteLederEpost shouldBeEqualTo "epost2@nav.no"
            nyNl?.narmesteLederTelefonnummer shouldBeEqualTo "40404040"

            coVerify(exactly = 1) { narmesteLederLeesahProducer.send(match { it.narmesteLederFnr == fnrLeder && it.status == DEAKTIVERT_NY_LEDER }) }
            coVerify(exactly = 1) { narmesteLederLeesahProducer.send(match { it.narmesteLederFnr == fnrLeder2 && it.status == NY_LEDER }) }
        }
        test("Kan ha to aktive NL samtidig hvis ulikt orgnummer") {
            testDb.connection.lagreNarmesteleder(orgnummer = "orgnummer", fnr = sykmeldtFnr, fnrNl = fnrLeder, arbeidsgiverForskutterer = true, aktivFom = OffsetDateTime.now(ZoneOffset.UTC).minusYears(1))
            val nlResponseKafkaMessage = NlResponseKafkaMessage(
                kafkaMetadata = KafkaMetadata(timestamp, "syfonlaltinn"),
                nlResponse = NlResponse(
                    "orgnummer2", utbetalesLonn = false, Leder(fnrLeder2, "40404040", "epost2@nav.no", "Leder", "Ledersen"),
                    Sykmeldt(sykmeldtFnr, "Syk Sykesen")
                ),
                nlAvbrutt = null
            )

            oppdaterNarmesteLederService.handterMottattNarmesteLederOppdatering(nlResponseKafkaMessage, 0, 0)

            val nlListe = testDb.finnAlleNarmesteledereForSykmeldt(sykmeldtFnr)
            nlListe.size shouldBeEqualTo 2
            val nlOrgnummer1 = nlListe.find { it.orgnummer == "orgnummer" }
            val nlOrgnummer2 = nlListe.find { it.orgnummer == "orgnummer2" }
            nlOrgnummer1?.narmesteLederFnr shouldBeEqualTo fnrLeder
            nlOrgnummer1?.aktivFom shouldBeEqualTo OffsetDateTime.now(ZoneOffset.UTC).minusYears(1).toLocalDate()
            nlOrgnummer1?.aktivTom shouldBeEqualTo null
            nlOrgnummer2?.narmesteLederFnr shouldBeEqualTo fnrLeder2
            nlOrgnummer2?.aktivFom shouldBeEqualTo timestamp.toLocalDate()
            nlOrgnummer2?.aktivTom shouldBeEqualTo null
            nlOrgnummer2?.arbeidsgiverForskutterer shouldBeEqualTo false
        }
        test("Feiler hvis ansatt ikke finnes i PDL") {
            coEvery { pdlPersonService.getPersoner(any(), any()) } returns mapOf(
                Pair(fnrLeder, PdlPerson(Navn("Leder", null, "Ledersen"), fnrLeder, "aktorid")),
                Pair(sykmeldtFnr, null)
            )
            val nlResponseKafkaMessage = NlResponseKafkaMessage(
                kafkaMetadata = KafkaMetadata(timestamp, "syfonlaltinn"),
                nlResponse = NlResponse(
                    "orgnummer", utbetalesLonn = true, Leder(fnrLeder, "90909090", "epost@nav.no", "Leder", "Ledersen"),
                    Sykmeldt(sykmeldtFnr, "Syk Sykesen")
                ),
                nlAvbrutt = null
            )

            assertFailsWith<IllegalStateException> {
                runBlocking {
                    oppdaterNarmesteLederService.handterMottattNarmesteLederOppdatering(nlResponseKafkaMessage, 0, 0)
                }
            }
        }
        test("Feiler hvis nærmeste leder ikke finnes i PDL") {
            coEvery { pdlPersonService.getPersoner(any(), any()) } returns mapOf(
                Pair(fnrLeder, null),
                Pair(sykmeldtFnr, PdlPerson(Navn("Syk", null, "Sykesen"), sykmeldtFnr, "aktorid2"))
            )
            val nlResponseKafkaMessage = NlResponseKafkaMessage(
                kafkaMetadata = KafkaMetadata(timestamp, "syfonlaltinn"),
                nlResponse = NlResponse(
                    "orgnummer", utbetalesLonn = true, Leder(fnrLeder, "90909090", "epost@nav.no", "Leder", "Ledersen"),
                    Sykmeldt(sykmeldtFnr, "Syk Sykesen")
                ),
                nlAvbrutt = null
            )

            assertFailsWith<IllegalStateException> {
                runBlocking {
                    oppdaterNarmesteLederService.handterMottattNarmesteLederOppdatering(nlResponseKafkaMessage, 0, 0)
                }
            }
        }
        test("NlAvbrutt deaktiverer nærmeste leder og ber om ny NL hvis arbeidsforholdet er aktivt") {
            coEvery { arbeidsgiverService.getArbeidsgivere(any(), any(), any()) } returns listOf(Arbeidsgiverinfo("orgnummer", "juridiskOrgnummer", aktivtArbeidsforhold = true))
            testDb.connection.lagreNarmesteleder(orgnummer = "orgnummer", fnr = sykmeldtFnr, fnrNl = fnrLeder, arbeidsgiverForskutterer = true, aktivFom = OffsetDateTime.now(ZoneOffset.UTC).minusYears(1))
            val aktivTom = OffsetDateTime.now(ZoneOffset.UTC)
            val nlResponseKafkaMessage = NlResponseKafkaMessage(
                kafkaMetadata = KafkaMetadata(timestamp, "syfosmaltinn"),
                nlResponse = null,
                nlAvbrutt = NlAvbrutt(
                    orgnummer = "orgnummer",
                    sykmeldtFnr = sykmeldtFnr,
                    aktivTom = aktivTom
                )
            )

            oppdaterNarmesteLederService.handterMottattNarmesteLederOppdatering(nlResponseKafkaMessage, 0, 0)

            val nlListe = testDb.finnAlleNarmesteledereForSykmeldt(sykmeldtFnr)
            nlListe.size shouldBeEqualTo 1
            nlListe[0].aktivTom shouldBeEqualTo aktivTom.toLocalDate()

            coVerify(exactly = 1) { narmesteLederLeesahProducer.send(match { it.status == DEAKTIVERT_ARBEIDSTAKER_INNSENDT_SYKMELDING }) }
            coVerify(exactly = 1) { nlRequestProducer.send(match { it.nlRequest.fnr == sykmeldtFnr && it.nlRequest.orgnr == "orgnummer" && it.metadata.source == "syfosmaltinn" }) }
        }
        test("NlAvbrutt deaktiverer nærmeste leder av leder og ber om ny NL hvis arbeidsforholdet er aktivt") {
            coEvery { arbeidsgiverService.getArbeidsgivere(any(), any(), any()) } returns listOf(Arbeidsgiverinfo("orgnummer", "juridiskOrgnummer", aktivtArbeidsforhold = true))
            testDb.connection.lagreNarmesteleder(orgnummer = "orgnummer", fnr = sykmeldtFnr, fnrNl = fnrLeder, arbeidsgiverForskutterer = true, aktivFom = OffsetDateTime.now(ZoneOffset.UTC).minusYears(1))
            val aktivTom = OffsetDateTime.now(ZoneOffset.UTC)
            val nlResponseKafkaMessage = NlResponseKafkaMessage(
                kafkaMetadata = KafkaMetadata(timestamp, "leder"),
                nlResponse = null,
                nlAvbrutt = NlAvbrutt(
                    orgnummer = "orgnummer",
                    sykmeldtFnr = sykmeldtFnr,
                    aktivTom = aktivTom
                )
            )

            oppdaterNarmesteLederService.handterMottattNarmesteLederOppdatering(nlResponseKafkaMessage, 0, 0)

            coVerify(exactly = 1) { narmesteLederLeesahProducer.send(match { it.status == DEAKTIVERT_LEDER }) }
            coVerify(exactly = 1) { nlRequestProducer.send(match { it.nlRequest.fnr == sykmeldtFnr && it.nlRequest.orgnr == "orgnummer" && it.metadata.source == "leder" }) }
        }
        test("NlAvbrutt deaktiverer nærmeste leder av arbeidstaker") {
            coEvery { arbeidsgiverService.getArbeidsgivere(any(), any(), any()) } returns listOf(Arbeidsgiverinfo("orgnummer", "juridiskOrgnummer", aktivtArbeidsforhold = true))
            testDb.connection.lagreNarmesteleder(orgnummer = "orgnummer", fnr = sykmeldtFnr, fnrNl = fnrLeder, arbeidsgiverForskutterer = true, aktivFom = OffsetDateTime.now(ZoneOffset.UTC).minusYears(1))
            val aktivTom = OffsetDateTime.now(ZoneOffset.UTC)
            val nlResponseKafkaMessage = NlResponseKafkaMessage(
                kafkaMetadata = KafkaMetadata(timestamp, "arbeidstaker"),
                nlResponse = null,
                nlAvbrutt = NlAvbrutt(
                    orgnummer = "orgnummer",
                    sykmeldtFnr = sykmeldtFnr,
                    aktivTom = aktivTom
                )
            )

            oppdaterNarmesteLederService.handterMottattNarmesteLederOppdatering(nlResponseKafkaMessage, 0, 0)

            coVerify(exactly = 1) { narmesteLederLeesahProducer.send(match { it.status == DEAKTIVERT_ARBEIDSTAKER }) }
            coVerify(exactly = 1) { nlRequestProducer.send(match { it.nlRequest.fnr == sykmeldtFnr && it.nlRequest.orgnr == "orgnummer" && it.metadata.source == "arbeidstaker" }) }
        }
        test("NlAvbrutt deaktiverer nærmeste leder av arbeidstaker og ber ikke om ny leder hvis arbeidsforholdet er inaktivt") {
            coEvery { arbeidsgiverService.getArbeidsgivere(any(), any(), any()) } returns listOf(Arbeidsgiverinfo("orgnummer", "juridiskOrgnummer", aktivtArbeidsforhold = false))
            testDb.connection.lagreNarmesteleder(orgnummer = "orgnummer", fnr = sykmeldtFnr, fnrNl = fnrLeder, arbeidsgiverForskutterer = true, aktivFom = OffsetDateTime.now(ZoneOffset.UTC).minusYears(1))
            val aktivTom = OffsetDateTime.now(ZoneOffset.UTC)
            val nlResponseKafkaMessage = NlResponseKafkaMessage(
                kafkaMetadata = KafkaMetadata(timestamp, "arbeidstaker"),
                nlResponse = null,
                nlAvbrutt = NlAvbrutt(
                    orgnummer = "orgnummer",
                    sykmeldtFnr = sykmeldtFnr,
                    aktivTom = aktivTom
                )
            )

            oppdaterNarmesteLederService.handterMottattNarmesteLederOppdatering(nlResponseKafkaMessage, 0, 0)

            coVerify(exactly = 1) { narmesteLederLeesahProducer.send(match { it.status == DEAKTIVERT_ARBEIDSTAKER }) }
            coVerify(exactly = 0) { nlRequestProducer.send(any()) }
        }
        test("NlAvbrutt deaktiverer nærmeste leder av arbeidstaker og ber ikke om ny leder hvis arbeidsforhold ikke finnes i listen") {
            coEvery { arbeidsgiverService.getArbeidsgivere(any(), any(), any()) } returns listOf(Arbeidsgiverinfo("orgnummer2", "juridiskOrgnummer", aktivtArbeidsforhold = false))
            testDb.connection.lagreNarmesteleder(orgnummer = "orgnummer", fnr = sykmeldtFnr, fnrNl = fnrLeder, arbeidsgiverForskutterer = true, aktivFom = OffsetDateTime.now(ZoneOffset.UTC).minusYears(1))
            val aktivTom = OffsetDateTime.now(ZoneOffset.UTC)
            val nlResponseKafkaMessage = NlResponseKafkaMessage(
                kafkaMetadata = KafkaMetadata(timestamp, "arbeidstaker"),
                nlResponse = null,
                nlAvbrutt = NlAvbrutt(
                    orgnummer = "orgnummer",
                    sykmeldtFnr = sykmeldtFnr,
                    aktivTom = aktivTom
                )
            )

            oppdaterNarmesteLederService.handterMottattNarmesteLederOppdatering(nlResponseKafkaMessage, 0, 0)

            coVerify(exactly = 1) { narmesteLederLeesahProducer.send(match { it.status == DEAKTIVERT_ARBEIDSTAKER }) }
            coVerify(exactly = 0) { nlRequestProducer.send(any()) }
        }
        test("NlAvbrutt feiler ikke hvis det ikke er noen ledere å deaktivere, og påvirker ikke nl for andre arbeidsforhold") {
            testDb.connection.lagreNarmesteleder(orgnummer = "orgnummer2", fnr = sykmeldtFnr, fnrNl = fnrLeder, arbeidsgiverForskutterer = true, aktivFom = OffsetDateTime.now(ZoneOffset.UTC).minusYears(1))
            val aktivTom = OffsetDateTime.now(ZoneOffset.UTC)
            val nlResponseKafkaMessage = NlResponseKafkaMessage(
                kafkaMetadata = KafkaMetadata(timestamp, "syfosmaltinn"),
                nlResponse = null,
                nlAvbrutt = NlAvbrutt(
                    orgnummer = "orgnummer",
                    sykmeldtFnr = sykmeldtFnr,
                    aktivTom = aktivTom
                )
            )

            oppdaterNarmesteLederService.handterMottattNarmesteLederOppdatering(nlResponseKafkaMessage, 0, 0)

            val nlListe = testDb.finnAlleNarmesteledereForSykmeldt(sykmeldtFnr)
            nlListe.size shouldBeEqualTo 1
            nlListe[0].aktivTom shouldBeEqualTo null
        }
    }
})
