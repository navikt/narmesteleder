package no.nav.syfo.narmesteleder.oppdatering.kafka

import no.nav.syfo.log
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.NarmesteLederLeesah
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class NarmesteLederLeesahProducer(
    private val kafkaProducer: KafkaProducer<String, NarmesteLederLeesah>,
    private val topicName: String
) {
    fun send(narmesteLederLeesah: NarmesteLederLeesah) {
        try {
            kafkaProducer
                .send(
                    ProducerRecord(
                        topicName,
                        narmesteLederLeesah.narmesteLederId.toString(),
                        narmesteLederLeesah
                    )
                )
                .get()
        } catch (ex: Exception) {
            log.error(
                "Noe gikk galt ved skriving av narmesteleder til leesah-topic for id ${narmesteLederLeesah.narmesteLederId}",
                ex.message
            )
            throw ex
        }
    }
}
