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
    private val oppdaterNarmesteLederService: OppdaterNarmesteLederService,
    private val cluster: String
) {

    suspend fun startConsumer() {

        kafkaConsumer.subscribe(listOf(topic))
        log.info("Starting consuming topic $topic")
        while (applicationState.ready) {
            kafkaConsumer.poll(Duration.ZERO).forEach {
                try {
                    oppdaterNarmesteLederService.handterMottattNarmesteLederOppdatering(it.value(), it.partition(), it.offset())
                } catch (e: Exception) {
                    if (cluster == "dev-gcp") {
                        log.error("Noe gikk galt ved mottak av melding med offset ${it.offset()}: ${e.message}, ignoreres i dev")
                    } else {
                        log.error("Noe gikk galt ved mottak av melding med offset ${it.offset()}: ${e.message}")
                        throw e
                    }
                }
            }
        }
    }
}
