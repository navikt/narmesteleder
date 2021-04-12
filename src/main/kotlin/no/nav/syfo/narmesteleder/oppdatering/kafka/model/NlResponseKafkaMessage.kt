package no.nav.syfo.narmesteleder.oppdatering.kafka.model

import no.nav.syfo.narmesteleder.oppdatering.model.NlAvbrutt
import no.nav.syfo.narmesteleder.oppdatering.model.NlResponse

data class NlResponseKafkaMessage(
    val kafkaMetadata: KafkaMetadata,
    val nlResponse: NlResponse?,
    val nlAvbrutt: NlAvbrutt?
)
