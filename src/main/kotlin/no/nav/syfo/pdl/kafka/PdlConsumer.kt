package no.nav.syfo.pdl.kafka

import no.nav.syfo.pdl.model.Navn
import org.apache.kafka.clients.consumer.KafkaConsumer

data class Personhendelse(val type: String, val navn: Navn)

class PdlConsumer(private val kafkaConsumer: KafkaConsumer<String, Personhendelse>) {}
