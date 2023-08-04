package no.nav.syfo.application

import io.ktor.server.engine.ApplicationEngine
import java.util.concurrent.TimeUnit
import no.nav.syfo.application.leaderelection.LeadershipHandling

class ApplicationServer(
    private val applicationServer: ApplicationEngine,
    private val applicationState: ApplicationState,
    private val leadershipHandling: LeadershipHandling
) {
    init {
        Runtime.getRuntime()
            .addShutdownHook(
                Thread {
                    this.applicationState.ready = false
                    leadershipHandling.stop()
                    this.applicationServer.stop(
                        TimeUnit.SECONDS.toMillis(10),
                        TimeUnit.SECONDS.toMillis(10)
                    )
                },
            )
    }

    fun start() {
        applicationServer.start(true)
    }
}
