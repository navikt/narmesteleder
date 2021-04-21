package no.nav.syfo.narmesteleder.oppdatering

import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.db.finnAlleNarmesteledereForSykmeldt
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.KafkaMetadata
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
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertFailsWith

@KtorExperimentalAPI
class OppdaterNarmesteLederServiceTest : Spek({
    val pdlPersonService = mockk<PdlPersonService>()
    val testDb = TestDB()
    val oppdaterNarmesteLederService = OppdaterNarmesteLederService(pdlPersonService, testDb)
    val sykmeldtFnr = "fnr"
    val fnrLeder = "123"
    val fnrLeder2 = "123456"
    val timestamp = OffsetDateTime.now(ZoneOffset.UTC)

    beforeEachTest {
        clearMocks(pdlPersonService)
        coEvery { pdlPersonService.getPersoner(any(), any()) } returns mapOf(
            Pair(fnrLeder, PdlPerson(Navn("Leder", null, "Ledersen"), fnrLeder, "aktorid")),
            Pair(fnrLeder2, PdlPerson(Navn("Leder", null, "Ledersen"), fnrLeder2, "aktorid2")),
            Pair(sykmeldtFnr, PdlPerson(Navn("Syk", null, "Sykesen"), sykmeldtFnr, "aktorid3"))
        )
    }
    afterEachTest {
        testDb.connection.dropData()
    }
    afterGroup {
        testDb.stop()
    }

    describe("OppdaterNarmesteLederService") {
        it("Oppretter ny nærmeste leder hvis ingen finnes fra før") {
            val nlResponseKafkaMessage = NlResponseKafkaMessage(
                kafkaMetadata = KafkaMetadata(timestamp, "altinn"),
                nlResponse = NlResponse(
                    "orgnummer", utbetalesLonn = true, Leder(fnrLeder, "90909090", "epost@nav.no", "Leder", "Ledersen"),
                    Sykmeldt(sykmeldtFnr, "Syk Sykesen")
                ),
                nlAvbrutt = null
            )

            runBlocking {
                oppdaterNarmesteLederService.handterMottattNarmesteLederOppdatering(nlResponseKafkaMessage)
            }

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
        }
        it("Oppdaterer nærmeste leder hvis finnes fra før") {
            testDb.connection.lagreNarmesteleder(orgnummer = "orgnummer", fnr = sykmeldtFnr, fnrNl = fnrLeder, arbeidsgiverForskutterer = true, aktivFom = OffsetDateTime.now(ZoneOffset.UTC).minusYears(1))
            val nlResponseKafkaMessage = NlResponseKafkaMessage(
                kafkaMetadata = KafkaMetadata(timestamp, "altinn"),
                nlResponse = NlResponse(
                    "orgnummer", utbetalesLonn = false, Leder(fnrLeder, "90909090", "epost2@nav.no", "Leder", "Ledersen"),
                    Sykmeldt(sykmeldtFnr, "Syk Sykesen")
                ),
                nlAvbrutt = null
            )

            runBlocking {
                oppdaterNarmesteLederService.handterMottattNarmesteLederOppdatering(nlResponseKafkaMessage)
            }

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
        }
        it("Deaktiverer tidligere nærmeste leder hvis ny leder meldes inn for samme orgnummer") {
            testDb.connection.lagreNarmesteleder(orgnummer = "orgnummer", fnr = sykmeldtFnr, fnrNl = fnrLeder, arbeidsgiverForskutterer = true, aktivFom = OffsetDateTime.now(ZoneOffset.UTC).minusYears(1))
            val nlResponseKafkaMessage = NlResponseKafkaMessage(
                kafkaMetadata = KafkaMetadata(timestamp, "altinn"),
                nlResponse = NlResponse(
                    "orgnummer", utbetalesLonn = false, Leder(fnrLeder2, "40404040", "epost2@nav.no", "Leder", "Ledersen"),
                    Sykmeldt(sykmeldtFnr, "Syk Sykesen")
                ),
                nlAvbrutt = null
            )

            runBlocking {
                oppdaterNarmesteLederService.handterMottattNarmesteLederOppdatering(nlResponseKafkaMessage)
            }

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
        }
        it("Kan ha to aktive NL samtidig hvis ulikt orgnummer") {
            testDb.connection.lagreNarmesteleder(orgnummer = "orgnummer", fnr = sykmeldtFnr, fnrNl = fnrLeder, arbeidsgiverForskutterer = true, aktivFom = OffsetDateTime.now(ZoneOffset.UTC).minusYears(1))
            val nlResponseKafkaMessage = NlResponseKafkaMessage(
                kafkaMetadata = KafkaMetadata(timestamp, "altinn"),
                nlResponse = NlResponse(
                    "orgnummer2", utbetalesLonn = false, Leder(fnrLeder2, "40404040", "epost2@nav.no", "Leder", "Ledersen"),
                    Sykmeldt(sykmeldtFnr, "Syk Sykesen")
                ),
                nlAvbrutt = null
            )

            runBlocking {
                oppdaterNarmesteLederService.handterMottattNarmesteLederOppdatering(nlResponseKafkaMessage)
            }

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
        it("Feiler hvis ansatt ikke finnes i PDL") {
            coEvery { pdlPersonService.getPersoner(any(), any()) } returns mapOf(
                Pair(fnrLeder, PdlPerson(Navn("Leder", null, "Ledersen"), fnrLeder, "aktorid")),
                Pair(sykmeldtFnr, null)
            )
            val nlResponseKafkaMessage = NlResponseKafkaMessage(
                kafkaMetadata = KafkaMetadata(timestamp, "altinn"),
                nlResponse = NlResponse(
                    "orgnummer", utbetalesLonn = true, Leder(fnrLeder, "90909090", "epost@nav.no", "Leder", "Ledersen"),
                    Sykmeldt(sykmeldtFnr, "Syk Sykesen")
                ),
                nlAvbrutt = null
            )

            assertFailsWith<IllegalStateException> {
                runBlocking {
                    oppdaterNarmesteLederService.handterMottattNarmesteLederOppdatering(nlResponseKafkaMessage)
                }
            }
        }
        it("Feiler hvis nærmeste leder ikke finnes i PDL") {
            coEvery { pdlPersonService.getPersoner(any(), any()) } returns mapOf(
                Pair(fnrLeder, null),
                Pair(sykmeldtFnr, PdlPerson(Navn("Syk", null, "Sykesen"), sykmeldtFnr, "aktorid2"))
            )
            val nlResponseKafkaMessage = NlResponseKafkaMessage(
                kafkaMetadata = KafkaMetadata(timestamp, "altinn"),
                nlResponse = NlResponse(
                    "orgnummer", utbetalesLonn = true, Leder(fnrLeder, "90909090", "epost@nav.no", "Leder", "Ledersen"),
                    Sykmeldt(sykmeldtFnr, "Syk Sykesen")
                ),
                nlAvbrutt = null
            )

            assertFailsWith<IllegalStateException> {
                runBlocking {
                    oppdaterNarmesteLederService.handterMottattNarmesteLederOppdatering(nlResponseKafkaMessage)
                }
            }
        }
        it("NlAvbrutt deaktiverer nærmeste leder") {
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

            runBlocking {
                oppdaterNarmesteLederService.handterMottattNarmesteLederOppdatering(nlResponseKafkaMessage)
            }

            val nlListe = testDb.finnAlleNarmesteledereForSykmeldt(sykmeldtFnr)
            nlListe.size shouldBeEqualTo 1
            nlListe[0].aktivTom shouldBeEqualTo aktivTom.toLocalDate()
        }
        it("NlAvbrutt feiler ikke hvis det ikke er noen ledere å deaktivere, og påvirker ikke nl for andre arbeidsforhold") {
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

            runBlocking {
                oppdaterNarmesteLederService.handterMottattNarmesteLederOppdatering(nlResponseKafkaMessage)
            }

            val nlListe = testDb.finnAlleNarmesteledereForSykmeldt(sykmeldtFnr)
            nlListe.size shouldBeEqualTo 1
            nlListe[0].aktivTom shouldBeEqualTo null
        }
    }
})
