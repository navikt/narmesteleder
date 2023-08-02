package no.nav.syfo.pdl.kafka

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.db.DatabaseInterface
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory

class PdlLeesahConsumer(
    private val kafkaConsumer: KafkaConsumer<String, Personhendelse>,
    private val applicationState: ApplicationState,
    private val topic: String,
    private val database: DatabaseInterface,
) {

    private val logger = LoggerFactory.getLogger(PdlLeesahConsumer::class.java)

    companion object {
        private const val DELAY_ON_ERROR_SECONDS = 60L
        private const val POLL_DURATION_SECONDS = 1L
    }

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
            val personhendelser =
                withContext(Dispatchers.IO) {
                    kafkaConsumer.poll(java.time.Duration.ofMillis(100)).mapNotNull { it.value() }
                }
            handlePersonhendelser(personhendelser)
        }
    }

    suspend fun handlePersonhendelser(personhendelser: List<Personhendelse>) {
        personhendelser.forEach { personhendelse ->
            // database.updateName(getFnr(personhendelse.personidenter) personhendelse.navn)
        }
    }
}
