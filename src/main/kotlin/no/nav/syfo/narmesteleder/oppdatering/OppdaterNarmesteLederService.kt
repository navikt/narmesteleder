package no.nav.syfo.narmesteleder.oppdatering

import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.db.deaktiverNarmesteLeder
import no.nav.syfo.db.finnAlleNarmesteledereForSykmeldt
import no.nav.syfo.db.lagreNarmesteLeder
import no.nav.syfo.db.oppdaterAktivFom
import no.nav.syfo.db.oppdaterNarmesteLeder
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.NarmesteLederRelasjon
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.NlResponseKafkaMessage
import no.nav.syfo.narmesteleder.oppdatering.model.NlResponse
import no.nav.syfo.pdl.service.PdlPersonService
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.xml.bind.JAXBElement

@KtorExperimentalAPI
class OppdaterNarmesteLederService(
    private val pdlPersonService: PdlPersonService,
    private val database: DatabaseInterface
) {

    private var counter = 0
    private var macgyverCounter = 0

    init {
        GlobalScope.launch {
            while (true) {
                log.info("Macgyver $macgyverCounter, Total: $counter")
                delay(10_000)
            }
        }
    }

    suspend fun handterMottattNarmesteLederOppdatering(nlResponseKafkaMessage: NlResponseKafkaMessage) {
        val callId = UUID.randomUUID().toString()
        counter += 1
        if(nlResponseKafkaMessage.kafkaMetadata.source != "macgyver") {
            return
        }
        macgyverCounter += 1
        when {
            nlResponseKafkaMessage.nlResponse != null -> {
                val sykmeldtFnr = nlResponseKafkaMessage.nlResponse.sykmeldt.fnr
                val nlFnr = nlResponseKafkaMessage.nlResponse.leder.fnr
//                val orgnummer = nlResponseKafkaMessage.nlResponse.orgnummer
//                val personMap = pdlPersonService.getPersoner(listOf(sykmeldtFnr, nlFnr), callId)
//                if (personMap[sykmeldtFnr] == null || personMap[nlFnr] == null) {
//                    log.error("Mottatt NL-skjema for ansatt eller leder som ikke finnes i PDL $callId")
//                    throw IllegalStateException("Mottatt NL-skjema for ansatt eller leder som ikke finnes i PDL")
//                }
                //val narmesteLedere = database.finnAlleNarmesteledereForSykmeldt(fnr = sykmeldtFnr, orgnummer = orgnummer)
                if (nlResponseKafkaMessage.kafkaMetadata.source == "macgyver") {
                    handterMigrertNarmesteLeder(nlResponseKafkaMessage)
                }
//                else {
//                    deaktiverTidligereLedere(narmesteLedere, OffsetDateTime.now(ZoneOffset.UTC), callId)
//                    createOrUpdateNL(narmesteLedere, nlResponseKafkaMessage, callId)
//                }
            }
            nlResponseKafkaMessage.nlAvbrutt != null -> {
//                val narmesteLedere = database.finnAlleNarmesteledereForSykmeldt(fnr = nlResponseKafkaMessage.nlAvbrutt.sykmeldtFnr, orgnummer = nlResponseKafkaMessage.nlAvbrutt.orgnummer)
//                deaktiverTidligereLedere(narmesteLedere, nlResponseKafkaMessage.nlAvbrutt.aktivTom, callId)
            }
            else -> {
                log.error("Har mottatt nl-response som ikke er ny eller avbrutt")
                throw IllegalStateException("Har mottatt nl-response som ikke er ny eller avbrutt")
            }
        }
    }

    fun handterMigrertNarmesteLeder(nlResponseKafkaMessage: NlResponseKafkaMessage) {
            database.lagreNarmesteLeder(nlResponseKafkaMessage.nlResponse!!, nlResponseKafkaMessage.kafkaMetadata.timestamp)
    }

    private fun skalDeaktivereTidligereLederIfmMigrering(sammeNarmesteLeder: NarmesteLederRelasjon, nlResponse: NlResponse): Boolean {
        return sammeNarmesteLeder.aktivTom == null && nlResponse.aktivTom != null &&
            sammeNarmesteLeder.timestamp.isBefore(OffsetDateTime.of(LocalDate.of(2021, 4, 8).atStartOfDay(), ZoneOffset.UTC))
    }

    private fun deaktiverTidligereLedere(narmesteLedere: List<NarmesteLederRelasjon>, aktivTom: OffsetDateTime, callId: String) {
        log.info("Deaktiverer ${narmesteLedere.size} nærmeste ledere $callId")
        narmesteLedere.filter { it.aktivTom == null }
            .forEach { database.deaktiverNarmesteLeder(narmesteLederId = it.narmesteLederId, aktivTom = aktivTom) }
    }

    private fun createOrUpdateNL(ledere: List<NarmesteLederRelasjon>, nlResponseKafkaMessage: NlResponseKafkaMessage, callId: String) {
        when (val eksisterendeLeder = getExistingLeder(ledere, nlResponseKafkaMessage.nlResponse!!.leder.fnr)) {
            null -> {
                database.lagreNarmesteLeder(nlResponseKafkaMessage.nlResponse, nlResponseKafkaMessage.kafkaMetadata.timestamp)
                log.info("Created new NL for callId $callId")
            }
            else -> {
                database.oppdaterNarmesteLeder(eksisterendeLeder.narmesteLederId, nlResponseKafkaMessage.nlResponse)
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
