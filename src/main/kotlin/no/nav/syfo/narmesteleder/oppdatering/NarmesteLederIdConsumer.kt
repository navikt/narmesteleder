package no.nav.syfo.narmesteleder.oppdatering

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.db.getNarmestelederRelasjonForId
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.oppdatering.kafka.NarmesteLederLeesahProducer
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.NarmesteLederLeesah
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration
import java.util.UUID

class NarmesteLederIdConsumer(
    private val kafkaConsumer: KafkaConsumer<String, String>,
    private val applicationState: ApplicationState,
    private val topic: String,
    private val narmesteLederLeesahProducer: NarmesteLederLeesahProducer,
    private val database: DatabaseInterface
) {

    fun startConsumer() {
        kafkaConsumer.subscribe(listOf(topic))
        log.info("Starting consuming topic $topic")
        while (applicationState.ready) {
            kafkaConsumer.poll(Duration.ZERO).forEach {
                try {
                    val narmesteLederRelasjon = database.getNarmestelederRelasjonForId(UUID.fromString(it.value()))
                    if (narmesteLederRelasjon == null) {
                        log.error("Fant ikke nærmeste leder-relasjon med id ${it.value()}")
                        throw RuntimeException("Fant ikke nærmeste leder. Skal ikke kunne skje!")
                    } else {
                        narmesteLederLeesahProducer.send(
                            NarmesteLederLeesah(
                                narmesteLederId = narmesteLederRelasjon.narmesteLederId,
                                fnr = narmesteLederRelasjon.fnr,
                                orgnummer = narmesteLederRelasjon.orgnummer,
                                narmesteLederFnr = narmesteLederRelasjon.narmesteLederFnr,
                                narmesteLederTelefonnummer = narmesteLederRelasjon.narmesteLederTelefonnummer,
                                narmesteLederEpost = narmesteLederRelasjon.narmesteLederEpost,
                                aktivFom = narmesteLederRelasjon.aktivFom,
                                aktivTom = narmesteLederRelasjon.aktivTom,
                                arbeidsgiverForskutterer = narmesteLederRelasjon.arbeidsgiverForskutterer,
                                timestamp = narmesteLederRelasjon.timestamp
                            )
                        )
                    }
                } catch (e: Exception) {
                    log.error("Noe gikk galt ved prosessering av id-melding med id ${it.value()}: ${e.message}")
                    throw e
                }
            }
        }
    }
}
