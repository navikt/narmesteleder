package no.nav.syfo.narmesteleder.oppdatering.kafka

import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.oppdatering.OppdaterNarmesteLederService
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.NlResponseKafkaMessage
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

@KtorExperimentalAPI
class NarmesteLederResponseConsumerService(
    private val kafkaConsumer: KafkaConsumer<String, NlResponseKafkaMessage>,
    private val applicationState: ApplicationState,
    private val topic: String,
    private val oppdaterNarmesteLederService: OppdaterNarmesteLederService
) {

    suspend fun startConsumer() {

        kafkaConsumer.subscribe(listOf(topic))
        log.info("Starting consuming topic $topic")
        while (applicationState.ready) {
            kafkaConsumer.poll(Duration.ZERO).forEach {
                oppdaterNarmesteLederService.handterMottattNarmesteLederOppdatering(it.value(), it.timestamp())
            }
        }
    }
}
