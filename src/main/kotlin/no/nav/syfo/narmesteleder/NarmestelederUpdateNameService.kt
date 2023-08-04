package no.nav.syfo.narmesteleder

import kotlin.time.Duration.Companion.seconds
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
import org.slf4j.LoggerFactory

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
                            val noNameList = database.getItemsWithoutNames()

                            if (noNameList.isEmpty()) break

                            identendringService.updateNames(noNameList)
                        } catch (ex: Exception) {
                            when (ex) {
                                is CancellationException -> {
                                    logger.warn("Job was cancelled, message: ${ex.message}")
                                    throw ex
                                }
                                else -> {
                                    logger.error("Caught unexpected delaying for 10s $ex")
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
