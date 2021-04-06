package no.nav.syfo.narmesteleder.oppdatering

import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.db.deaktiverNarmesteLeder
import no.nav.syfo.db.finnAlleNarmesteledereForSykmeldt
import no.nav.syfo.db.lagreNarmesteLeder
import no.nav.syfo.db.oppdaterNarmesteLeder
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.NarmesteLederRelasjon
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.NlResponseKafkaMessage
import no.nav.syfo.pdl.service.PdlPersonService
import java.util.UUID

@KtorExperimentalAPI
class OppdaterNarmesteLederService(
    private val pdlPersonService: PdlPersonService,
    private val database: DatabaseInterface
) {

    suspend fun handterMottattNarmesteLederOppdatering(nlResponseKafkaMessage: NlResponseKafkaMessage) {
        val callId = UUID.randomUUID().toString()
        val sykmeldtFnr = nlResponseKafkaMessage.nlResponse.sykmeldt.fnr
        val nlFnr = nlResponseKafkaMessage.nlResponse.leder.fnr
        val orgnummer = nlResponseKafkaMessage.nlResponse.orgnummer
        val navnMap = pdlPersonService.getPersonnavn(listOf(sykmeldtFnr, nlFnr), callId)
        if (navnMap[sykmeldtFnr] == null || navnMap[nlFnr] == null) {
            log.error("Mottatt NL-skjema for ansatt eller leder som ikke finnes i PDL $callId")
            throw IllegalStateException("Mottatt NL-skjema for ansatt eller leder som ikke finnes i PDL")
        }
        val narmesteLedere = database.finnAlleNarmesteledereForSykmeldt(fnr = sykmeldtFnr, orgnummer = orgnummer)
        deaktiverTidligereLedere(narmesteLedere, callId)

        createOrUpdateNL(narmesteLedere, nlResponseKafkaMessage, callId)
    }

    private fun deaktiverTidligereLedere(narmesteLedere: List<NarmesteLederRelasjon>, callId: String) {
        log.info("Deaktiverer ${narmesteLedere.size} nærmeste ledere $callId")
        narmesteLedere.filter { it.aktivTom == null }
            .forEach { database.deaktiverNarmesteLeder(it.narmesteLederId) }
    }

    private fun createOrUpdateNL(ledere: List<NarmesteLederRelasjon>, nlResponseKafkaMessage: NlResponseKafkaMessage, callId: String) {
        when (val eksisterendeLeder = getExistingLeder(ledere, nlResponseKafkaMessage.nlResponse.leder.fnr)) {
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
