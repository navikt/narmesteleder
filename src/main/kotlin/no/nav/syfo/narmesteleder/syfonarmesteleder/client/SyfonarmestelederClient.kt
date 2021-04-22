package no.nav.syfo.narmesteleder.syfonarmesteleder.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import no.nav.syfo.log

class SyfonarmestelederClient(
    private val httpClient: HttpClient,
    private val accessTokenClient: AccessTokenClient,
    private val baseUrl: String
) {
    suspend fun getAktiveNarmestelederKoblinger(narmesteLederAktorId: String, callId: String): SyfoNarmestelederResponse {
        val token = accessTokenClient.getAccessToken()
        try {
            val statement = httpClient.get<HttpStatement>("$baseUrl$NARMESTE_LEDER_URL/$narmesteLederAktorId") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                }
                accept(ContentType.Application.Json)
            }.execute()
            val status = statement.status
            log.info("Got status $status from NarmesteLeder")
            return statement.receive()
        } catch (e: Exception) {
            log.error("Kunne ikke hente nærmesteleder-koblinger fra syfonarmesteleder $callId")
            throw e
        }
    }

    companion object {
        private const val NARMESTE_LEDER_URL = "/syfonarmesteleder/narmesteLeder"
    }
}
