package no.nav.syfo.narmesteleder.oppdatering.kafka

import no.nav.syfo.log
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.NlRequestKafkaMessage
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class NLRequestProducer(
    private val kafkaProducer: KafkaProducer<String, NlRequestKafkaMessage>,
    private val topicName: String
) {
    fun send(nlRequestKafkaMessage: NlRequestKafkaMessage) {
        try {
            kafkaProducer
                .send(
                    ProducerRecord(
                        topicName,
                        nlRequestKafkaMessage.nlRequest.orgnr,
                        nlRequestKafkaMessage
                    )
                )
                .get()
        } catch (ex: Exception) {
            log.error(
                "Noe gikk galt ved skriving av NL-forespørsel til kafka for orgnummer {}, {}",
                nlRequestKafkaMessage.nlRequest.orgnr,
                ex.message
            )
            throw ex
        }
    }
}
