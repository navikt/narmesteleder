package no.nav.syfo.narmesteleder.oppdatering

import io.ktor.util.KtorExperimentalAPI
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

@KtorExperimentalAPI
class OppdaterNarmesteLederService(
    private val pdlPersonService: PdlPersonService,
    private val database: DatabaseInterface
) {

    suspend fun handterMottattNarmesteLederOppdatering(nlResponseKafkaMessage: NlResponseKafkaMessage) {
        val callId = UUID.randomUUID().toString()
        when {
            nlResponseKafkaMessage.nlResponse != null -> {
                val sykmeldtFnr = nlResponseKafkaMessage.nlResponse.sykmeldt.fnr
                val nlFnr = nlResponseKafkaMessage.nlResponse.leder.fnr
                val orgnummer = nlResponseKafkaMessage.nlResponse.orgnummer
                val personMap = pdlPersonService.getPersoner(listOf(sykmeldtFnr, nlFnr), callId)
                if (personMap[sykmeldtFnr] == null || personMap[nlFnr] == null) {
                    log.error("Mottatt NL-skjema for ansatt eller leder som ikke finnes i PDL $callId")
                    throw IllegalStateException("Mottatt NL-skjema for ansatt eller leder som ikke finnes i PDL")
                }
                val narmesteLedere = database.finnAlleNarmesteledereForSykmeldt(fnr = sykmeldtFnr, orgnummer = orgnummer)
                if (nlResponseKafkaMessage.kafkaMetadata.source == "macgyver") {
                    handterMigrertNarmesteLeder(narmesteLedere, nlResponseKafkaMessage, callId)
                } else {
                    deaktiverTidligereLedere(narmesteLedere, OffsetDateTime.now(ZoneOffset.UTC), callId)
                    createOrUpdateNL(narmesteLedere, nlResponseKafkaMessage, callId)
                }
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

    // Scenarier:
    // 1. Hvis vi ikke har registrert NL i databasen kan NL fra syfoservice legges inn as-is
    // 2. Hvis vi har data om en NL i databasen vil denne være mer oppdatert enn det vi får fra migreringen slik at vi kun trenger å oppdatere aktivFom,
    // bortsett fra hvis NL har blitt deaktivert i Syfoservice men står som aktiv her. Da må den deaktiveres også.
    // 3. Man kan ikke ha mer enn en aktiv leder pr arbeidsforhold, så hvis vi får inn melding om annen leder der vi allerede har registrert en leder og begge er aktive
    // må vi stoppe.
    fun handterMigrertNarmesteLeder(narmesteLedere: List<NarmesteLederRelasjon>, nlResponseKafkaMessage: NlResponseKafkaMessage, callId: String) {
        if (narmesteLedere.isEmpty()) {
            database.lagreNarmesteLeder(nlResponseKafkaMessage.nlResponse!!, nlResponseKafkaMessage.kafkaMetadata.timestamp)
            log.info("Lagret migrert NL for callId $callId")
        } else {
            val sammeNarmesteLeder = narmesteLedere.find { it.narmesteLederFnr == nlResponseKafkaMessage.nlResponse!!.leder.fnr }
            if (sammeNarmesteLeder != null) {
                database.oppdaterAktivFom(sammeNarmesteLeder.narmesteLederId, nlResponseKafkaMessage.nlResponse!!.aktivFom!!)
                log.info("Oppdatert aktivFom for narmesteleder ${sammeNarmesteLeder.narmesteLederId}, callid $callId")
                if (skalDeaktivereTidligereLederIfmMigrering(sammeNarmesteLeder, nlResponseKafkaMessage.nlResponse)) {
                    database.deaktiverNarmesteLeder(
                        sammeNarmesteLeder.narmesteLederId,
                        nlResponseKafkaMessage.nlResponse.aktivTom
                    )
                    log.info("Deaktivert narmeste leder med id ${sammeNarmesteLeder.narmesteLederId} som har blitt deaktivert i syfoservice, $callId")
                }
            } else {
                val andreAktiveNarmesteLedere = narmesteLedere.filter { it.narmesteLederFnr != nlResponseKafkaMessage.nlResponse!!.leder.fnr && it.aktivTom == null }
                if (andreAktiveNarmesteLedere.isNotEmpty()) {
                    if (nlResponseKafkaMessage.nlResponse!!.aktivTom != null) {
                        database.lagreNarmesteLeder(nlResponseKafkaMessage.nlResponse, nlResponseKafkaMessage.kafkaMetadata.timestamp)
                        log.info("Lagret migrert, tidligere NL for callId $callId")
                    } else {
                        log.error("Mottatt aktiv, migrert NL når det allerede finnes aktiv NL for arbeidsforholdet. NL-id ${andreAktiveNarmesteLedere.first().narmesteLederId}, callId: $callId")
                        throw IllegalStateException("Mottatt aktiv, migrert NL når det allerede finnes NL for arbeidsforholdet")
                    }
                }
            }
        }
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
