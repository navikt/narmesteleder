package no.nav.syfo.application.service

interface UpdateService {
    suspend fun start()

    suspend fun stop()
}
