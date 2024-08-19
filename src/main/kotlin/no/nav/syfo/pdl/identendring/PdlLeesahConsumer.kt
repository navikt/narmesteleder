package no.nav.syfo.pdl.identendring

import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.syfo.application.ApplicationState
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory

@OptIn(DelicateCoroutinesApi::class)
class PdlLeesahConsumer(
    private val kafkaConsumer: KafkaConsumer<String, Personhendelse>,
    private val applicationState: ApplicationState,
    private val topic: String,
    private val identendringService: IdentendringService,
) {

    private val logger = LoggerFactory.getLogger(PdlLeesahConsumer::class.java)

    companion object {
        private const val DELAY_ON_ERROR_SECONDS = 60L
        private const val POLL_DURATION_SECONDS = 1L
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun start() {
        GlobalScope.launch(Dispatchers.IO) {
            while (applicationState.alive) {
                try {
                    logger.info("Subscribing to $topic")
                    kafkaConsumer.subscribe(listOf(topic))
                    runConsumer()
                } catch (ex: Exception) {
                    logger.error("Error running kafkaConsumer for $topic", ex)
                    kafkaConsumer.unsubscribe()
                    delay(DELAY_ON_ERROR_SECONDS.seconds)
                }
            }
        }
    }

    suspend fun runConsumer() {
        while (applicationState.alive) {
            val personhendelser: ConsumerRecords<String, Personhendelse> =
                withContext(Dispatchers.IO) {
                    kafkaConsumer.poll(POLL_DURATION_SECONDS.seconds.toJavaDuration())
                }
            val identer = personhendelser.filter { it.value().navn != null }.mapNotNull {
                val fnr = it.key() ?: return@mapNotNull null

                val cleanFnr = fnr.replace(Regex("[^0-9]"), "")
                if (cleanFnr != fnr) {
                    logger.info("Got dirty fnr in Leesah consumer, cleaned it, topic ${it.topic()}")
                }

                cleanFnr
            }
            if (identer.isNotEmpty()) {
                identendringService.updateNames(identer)
            }
        }
    }
}
