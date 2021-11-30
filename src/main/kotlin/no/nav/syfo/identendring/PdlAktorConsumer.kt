package no.nav.syfo.identendring

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.person.pdl.aktor.v2.Aktor
import no.nav.person.pdl.aktor.v2.Type
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.coroutine.Unbounded
import no.nav.syfo.identendring.model.Ident
import no.nav.syfo.identendring.model.IdentType
import no.nav.syfo.log
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class PdlAktorConsumer(
    private val kafkaConsumer: KafkaConsumer<String, Aktor>,
    private val applicationState: ApplicationState,
    private val topic: String,
    private val identendringService: IdentendringService
) {
    companion object {
        private const val DELAY_ON_ERROR_SECONDS = 60L
        private const val POLL_DURATION_SECONDS = 10L
    }

    @ExperimentalTime
    @DelicateCoroutinesApi
    fun startConsumer() {
        GlobalScope.launch(Dispatchers.Unbounded) {
            while (applicationState.ready) {
                try {
                    runConsumer()
                } catch (ex: Exception) {
                    log.error("Error running kafka consumer for pdl-aktor, unsubscribing and waiting $DELAY_ON_ERROR_SECONDS seconds for retry", ex)
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
                    identendringService.oppdaterIdent(it.value().toIdentListe())
                } catch (e: Exception) {
                    log.error("Noe gikk galt ved mottak av pdl-aktor-melding med offset ${it.offset()}: ${e.message}")
                    throw e
                }
            }
        }
    }
}

fun Aktor.toIdentListe(): List<Ident> {
    return this.identifikatorer.map {
        Ident(
            idnummer = it.idnummer,
            gjeldende = it.gjeldende,
            type = when (it.type) {
                Type.FOLKEREGISTERIDENT -> IdentType.FOLKEREGISTERIDENT
                Type.AKTORID -> IdentType.AKTORID
                Type.NPID -> IdentType.NPID
                else -> throw IllegalStateException("Har mottatt ident med ukjent type")
            }
        )
    }
}
