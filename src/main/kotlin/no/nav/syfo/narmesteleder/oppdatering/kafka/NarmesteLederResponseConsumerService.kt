package no.nav.syfo.narmesteleder.oppdatering.kafka

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.coroutine.Unbounded
import no.nav.syfo.narmesteleder.oppdatering.OppdaterNarmesteLederService
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.NlResponseKafkaMessage
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@DelicateCoroutinesApi
class NarmesteLederResponseConsumerService(
    private val kafkaConsumer: KafkaConsumer<String, NlResponseKafkaMessage>,
    private val applicationState: ApplicationState,
    private val topic: String,
    private val oppdaterNarmesteLederService: OppdaterNarmesteLederService,
    private val cluster: String
) {

    companion object {
        private val log = LoggerFactory.getLogger(NarmesteLederResponseConsumerService::class.java)
        private const val DELAY_ON_ERROR_SECONDS = 60L
        private const val POLL_DURATION_SECONDS = 10L
    }

    @ExperimentalTime
    fun startConsumer() {
        GlobalScope.launch(Dispatchers.Unbounded) {
            while (applicationState.ready) {
                try {
                    runConsumer()
                } catch (ex: Exception) {
                    log.error("Error running kafka consumer, unsubscribing and waiting $DELAY_ON_ERROR_SECONDS seconds for retry", ex)
                    kafkaConsumer.unsubscribe()
                    delay(DELAY_ON_ERROR_SECONDS.seconds)
                }
            }
        }
    }

    private suspend fun runConsumer() {
        kafkaConsumer.subscribe(listOf(topic))
        log.info("Starting consuming topic $topic")
        while (applicationState.ready) {
            kafkaConsumer.poll(Duration.ofSeconds(POLL_DURATION_SECONDS)).forEach {
                try {
                    oppdaterNarmesteLederService.handterMottattNarmesteLederOppdatering(
                        it.value(),
                        it.partition(),
                        it.offset()
                    )
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
