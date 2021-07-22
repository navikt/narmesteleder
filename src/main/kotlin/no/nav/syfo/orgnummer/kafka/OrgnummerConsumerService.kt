package no.nav.syfo.orgnummer.kafka

import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
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
            var juridiskOrgnummerMangler = 0
            kafkaConsumer.subscribe(listOf(topic))
            while (true) {
                try {
                    val crs = kafkaConsumer.poll(Duration.ofSeconds(10))
                    if (crs.isEmpty) {
                        delay(Duration.ofSeconds(1))
                    } else {
                        val arbeidsgivere = crs.map { it.value().event.arbeidsgiver }
                        val utenJuridisk = arbeidsgivere.filter { it.juridiskOrgnummer.isNullOrEmpty() }
                        if (utenJuridisk.isNotEmpty()) {
                            juridiskOrgnummerMangler += utenJuridisk.size
                        }
                        database.saveOrUpdateOrgnummer(arbeidsgivere.filter { !it.juridiskOrgnummer.isNullOrEmpty() })
                    }
                    processedMessages += crs.count()
                    processedMessages = logProcessedMessages(processedMessages, juridiskOrgnummerMangler)
                } catch (ex: MissingKotlinParameterException) {
                    log.error("faild to map parameters ${ex.parameter}, ${ex.path}")
                    throw ex;
                } catch (ex: Exception) {
                    log.error("Error in orgnummer", ex.javaClass)
                    throw ex;
            }
        }
    }
    private fun logProcessedMessages(processedMessages: Int, juridiskOrgnummerMangler: Int): Int {
        val currentLogTime = Instant.now().toEpochMilli()
        if (processedMessages > 0 && currentLogTime - lastLogTime > logTimer) {
            no.nav.syfo.log.info("Processed $processedMessages messages, juridiskOrgnummer mangler $juridiskOrgnummerMangler")
            lastLogTime = currentLogTime
            return 0
        }
        return processedMessages
    }
}
