package no.nav.syfo.orgnummer.kafka

import kotlinx.coroutines.delay
import kotlinx.coroutines.time.delay
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.orgnummer.db.saveOrUpdateOrgnummer
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

class OrgnummerConsumerService(
    private val kafkaConsumer: KafkaConsumer<String, SendtSykmelding>,
    private val database: DatabaseInterface,
    private val topic: String
) {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(OrgnummerConsumerService::class.java)
    }
    private var lastLogTime = Instant.now().toEpochMilli()
    private val logTimer = 60_000L

    suspend fun startConsumer() {
        var processedMessages = 0
        kafkaConsumer.subscribe(listOf(topic))
        while (true) {
            val crs = kafkaConsumer.poll(Duration.ofSeconds(10))
            if (crs.isEmpty) {
                delay(Duration.ofSeconds(1))
            } else {
                log.info("read ${crs.count()} from kafka topic $topic")
                val pairs = crs.map { it.value().event.arbeidsgiver }
                database.saveOrUpdateOrgnummer(pairs)
            }
            processedMessages += crs.count()
            processedMessages = logProcessedMessages(processedMessages)
        }
    }
    private fun logProcessedMessages(processedMessages: Int): Int {
        val currentLogTime = Instant.now().toEpochMilli()
        if (processedMessages > 0 && currentLogTime - lastLogTime > logTimer) {
            no.nav.syfo.log.info("Processed $processedMessages messages")
            lastLogTime = currentLogTime
            return 0
        }
        return processedMessages
    }
}
