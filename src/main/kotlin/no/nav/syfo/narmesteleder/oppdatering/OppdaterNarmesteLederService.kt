package no.nav.syfo.narmesteleder.oppdatering

import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.db.deaktiverNarmesteLeder
import no.nav.syfo.db.finnAlleNarmesteledereForSykmeldt
import no.nav.syfo.db.lagreNarmesteLeder
import no.nav.syfo.db.oppdaterNarmesteLeder
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.NarmesteLederRelasjon
import no.nav.syfo.narmesteleder.oppdatering.kafka.NarmesteLederLeesahProducer
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.NarmesteLederLeesah
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.NlResponseKafkaMessage
import no.nav.syfo.pdl.service.PdlPersonService
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@KtorExperimentalAPI
class OppdaterNarmesteLederService(
    private val pdlPersonService: PdlPersonService,
    private val database: DatabaseInterface,
    private val narmesteLederLeesahProducer: NarmesteLederLeesahProducer
) {

    suspend fun handterMottattNarmesteLederOppdatering(
        nlResponseKafkaMessage: NlResponseKafkaMessage,
        partition: Int,
        offset: Long
    ) {
        val callId = UUID.randomUUID().toString()
        when {
            nlResponseKafkaMessage.nlResponse != null -> {
                val sykmeldtFnr = nlResponseKafkaMessage.nlResponse.sykmeldt.fnr
                val nlFnr = nlResponseKafkaMessage.nlResponse.leder.fnr
                val orgnummer = nlResponseKafkaMessage.nlResponse.orgnummer
                val personMap = pdlPersonService.getPersoner(listOf(sykmeldtFnr, nlFnr), callId)
                if (personMap[sykmeldtFnr] == null || personMap[nlFnr] == null) {
                    log.error("Mottatt NL-skjema for ansatt eller leder som ikke finnes i PDL callId $callId partition: $partition, offset: $offset")
                    throw IllegalStateException("Mottatt NL-skjema for ansatt eller leder som ikke finnes i PDL")
                }
                val narmesteLedere = database.finnAlleNarmesteledereForSykmeldt(fnr = sykmeldtFnr, orgnummer = orgnummer)
                deaktiverTidligereLedere(narmesteLedere, OffsetDateTime.now(ZoneOffset.UTC), callId)
                createOrUpdateNL(narmesteLedere, nlResponseKafkaMessage, callId)
            }
            nlResponseKafkaMessage.nlAvbrutt != null -> {
                val narmesteLedere = database.finnAlleNarmesteledereForSykmeldt(fnr = nlResponseKafkaMessage.nlAvbrutt.sykmeldtFnr, orgnummer = nlResponseKafkaMessage.nlAvbrutt.orgnummer)
                deaktiverTidligereLedere(narmesteLedere, nlResponseKafkaMessage.nlAvbrutt.aktivTom, callId)
            }
            else -> {
                log.error("Har mottatt nl-response som ikke er ny eller avbrutt")
                throw IllegalStateException("Har mottatt nl-response som ikke er ny eller avbrutt")
            }
        }
    }

    private fun deaktiverTidligereLedere(narmesteLedere: List<NarmesteLederRelasjon>, aktivTom: OffsetDateTime, callId: String) {
        log.info("Deaktiverer ${narmesteLedere.size} nærmeste ledere $callId")
        narmesteLedere.filter { it.aktivTom == null }
            .forEach {
                database.deaktiverNarmesteLeder(narmesteLederId = it.narmesteLederId, aktivTom = aktivTom)
                narmesteLederLeesahProducer.send(
                    NarmesteLederLeesah(
                        narmesteLederId = it.narmesteLederId,
                        fnr = it.fnr,
                        orgnummer = it.orgnummer,
                        narmesteLederFnr = it.narmesteLederFnr,
                        narmesteLederTelefonnummer = it.narmesteLederTelefonnummer,
                        narmesteLederEpost = it.narmesteLederEpost,
                        aktivFom = it.aktivFom,
                        aktivTom = aktivTom.toLocalDate(),
                        arbeidsgiverForskutterer = it.arbeidsgiverForskutterer,
                        timestamp = OffsetDateTime.now(ZoneOffset.UTC)
                    )
                )
            }
    }

    private fun createOrUpdateNL(ledere: List<NarmesteLederRelasjon>, nlResponseKafkaMessage: NlResponseKafkaMessage, callId: String) {
        when (val eksisterendeLeder = getExistingLeder(ledere, nlResponseKafkaMessage.nlResponse!!.leder.fnr)) {
            null -> {
                val narmesteLederId = UUID.randomUUID()
                database.lagreNarmesteLeder(narmesteLederId, nlResponseKafkaMessage.nlResponse, nlResponseKafkaMessage.kafkaMetadata.timestamp)
                narmesteLederLeesahProducer.send(
                    NarmesteLederLeesah(
                        narmesteLederId = narmesteLederId,
                        fnr = nlResponseKafkaMessage.nlResponse.sykmeldt.fnr,
                        orgnummer = nlResponseKafkaMessage.nlResponse.orgnummer,
                        narmesteLederFnr = nlResponseKafkaMessage.nlResponse.leder.fnr,
                        narmesteLederTelefonnummer = nlResponseKafkaMessage.nlResponse.leder.mobil,
                        narmesteLederEpost = nlResponseKafkaMessage.nlResponse.leder.epost,
                        aktivFom = nlResponseKafkaMessage.nlResponse.aktivFom?.let { nlResponseKafkaMessage.nlResponse.aktivFom.toLocalDate() }
                            ?: nlResponseKafkaMessage.kafkaMetadata.timestamp.toLocalDate(),
                        aktivTom = nlResponseKafkaMessage.nlResponse.aktivTom?.let { nlResponseKafkaMessage.nlResponse.aktivTom.toLocalDate() },
                        arbeidsgiverForskutterer = nlResponseKafkaMessage.nlResponse.utbetalesLonn,
                        timestamp = OffsetDateTime.now(ZoneOffset.UTC)
                    )
                )
                log.info("Created new NL for callId $callId")
            }
            else -> {
                database.oppdaterNarmesteLeder(eksisterendeLeder.narmesteLederId, nlResponseKafkaMessage.nlResponse)
                narmesteLederLeesahProducer.send(
                    NarmesteLederLeesah(
                        narmesteLederId = eksisterendeLeder.narmesteLederId,
                        fnr = eksisterendeLeder.fnr,
                        orgnummer = eksisterendeLeder.orgnummer,
                        narmesteLederFnr = eksisterendeLeder.narmesteLederFnr,
                        narmesteLederTelefonnummer = nlResponseKafkaMessage.nlResponse.leder.mobil,
                        narmesteLederEpost = nlResponseKafkaMessage.nlResponse.leder.epost,
                        aktivFom = eksisterendeLeder.aktivFom,
                        aktivTom = null,
                        arbeidsgiverForskutterer = nlResponseKafkaMessage.nlResponse.utbetalesLonn,
                        timestamp = OffsetDateTime.now(ZoneOffset.UTC)
                    )
                )
                log.info("Updating existing NL with id ${eksisterendeLeder.narmesteLederId}, $callId")
            }
        }
    }

    private fun getExistingLeder(
        ledere: List<NarmesteLederRelasjon>,
        nlFnr: String
    ): NarmesteLederRelasjon? {
        return ledere.maxByOrNull { it.aktivFom }.takeIf { it?.narmesteLederFnr == nlFnr }
    }
}
