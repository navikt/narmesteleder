package no.nav.syfo.narmesteleder.oppdatering.kafka.model

import java.time.OffsetDateTime

class KafkaMetadata(val timestamp: OffsetDateTime, val source: String)
