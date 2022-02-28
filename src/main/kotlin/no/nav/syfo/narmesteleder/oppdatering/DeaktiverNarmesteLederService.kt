package no.nav.syfo.narmesteleder.oppdatering

import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.db.finnAktiveNarmestelederkoblinger
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.oppdatering.kafka.NLResponseProducer
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.KafkaMetadata
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.NlResponseKafkaMessage
import no.nav.syfo.narmesteleder.oppdatering.model.NlAvbrutt
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class DeaktiverNarmesteLederService(
    private val nlResponseProducer: NLResponseProducer,
    private val database: DatabaseInterface
) {
    fun deaktiverNarmesteLederForAnsatt(fnrLeder: String, orgnummer: String, fnrSykmeldt: String, callId: UUID) {
        val aktuelleNlKoblinger = database.finnAktiveNarmestelederkoblinger(fnrLeder).filter { it.orgnummer == orgnummer && it.fnr == fnrSykmeldt }
        if (aktuelleNlKoblinger.isNotEmpty()) {
            log.info("Deaktiverer NL-koblinger for $callId")
            deaktiverNarmesteLeder(orgnummer = aktuelleNlKoblinger.first().orgnummer, fnrSykmeldt = aktuelleNlKoblinger.first().fnr, forespurtAvAnsatt = false)
        } else {
            log.info("Ingen aktive koblinger å deaktivere $callId")
        }
    }

    fun deaktiverNarmesteLeder(orgnummer: String, fnrSykmeldt: String, forespurtAvAnsatt: Boolean = true) {
        nlResponseProducer.send(
            NlResponseKafkaMessage(
                kafkaMetadata = KafkaMetadata(
                    timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                    source = getSource(forespurtAvAnsatt)
                ),
                nlAvbrutt = NlAvbrutt(
                    orgnummer = orgnummer,
                    sykmeldtFnr = fnrSykmeldt,
                    aktivTom = OffsetDateTime.now(ZoneOffset.UTC)
                ),
                nlResponse = null
            )
        )
    }

    private fun getSource(forespurtAvAnsatt: Boolean) = when (forespurtAvAnsatt) {
        true -> "arbeidstaker"
        false -> "leder"
    }
}
