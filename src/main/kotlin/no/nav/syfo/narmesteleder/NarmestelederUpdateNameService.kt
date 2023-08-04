package no.nav.syfo.narmesteleder

import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.application.service.UpdateService
import no.nav.syfo.db.getItemsWithoutNames
import no.nav.syfo.pdl.identendring.IdentendringService
import no.nav.syfo.securelog
import org.slf4j.LoggerFactory
import java.sql.BatchUpdateException

@OptIn(DelicateCoroutinesApi::class)
class NarmestelederUpdateNameService(
    private val database: DatabaseInterface,
    private val identendringService: IdentendringService
) : UpdateService {

    private var updateJob: Job? = null
    private val logger = LoggerFactory.getLogger(NarmestelederUpdateNameService::class.java)

    override suspend fun start() = coroutineScope {
        if (updateJob?.isActive != true) {
            updateJob =
                launch(Dispatchers.IO) {
                    while (isActive) {
                        try {

                            val (noNameList, duration) =
                                measureTimedValue { database.getItemsWithoutNames().distinct() }
                            val timeInSeconds = duration.inWholeSeconds
                            val leftoverMilliseconds = duration.inWholeMilliseconds % 1000
                            logger.info(
                                "getting noNames ${noNameList.size} from database in $timeInSeconds.$leftoverMilliseconds seconds"
                            )

                            if (noNameList.isEmpty()) break

                            identendringService.updateNames(noNameList)
                        } catch (ex: Exception) {
                            when (ex) {
                                is CancellationException -> {
                                    logger.warn("Job was cancelled, message: ${ex.message}")
                                    throw ex
                                }
                                is BatchUpdateException -> {
                                    logger.error("BatchUpdateException")
                                    securelog.error("error", ex)
                                }
                                else -> {
                                    logger.error("Caught unexpected delaying for 10s")
                                    delay(10.seconds)
                                }
                            }
                        }
                    }
                }
        }
    }

    override suspend fun stop() {
        updateJob?.cancelAndJoin()
        updateJob = null
    }
}
