package no.nav.syfo.narmesteleder.oppdatering

import no.nav.syfo.narmesteleder.oppdatering.kafka.NLResponseProducer
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.KafkaMetadata
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.NlResponseKafkaMessage
import no.nav.syfo.narmesteleder.oppdatering.model.NlAvbrutt
import java.time.OffsetDateTime
import java.time.ZoneOffset

class DeaktiverNarmesteLederService(
    private val nlResponseProducer: NLResponseProducer
) {
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
