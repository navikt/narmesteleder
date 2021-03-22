package no.nav.syfo.forskuttering

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import org.slf4j.MDC

class ForskutteringsClient(
    private val endpointUrl: String,
    private val resourceId: String,
    private val client: HttpClient
) {
    suspend fun hentForskutteringFraSyfoserviceStrangler(aktorId: String, orgnummer: String): ForskutteringRespons {
        return client.get("$endpointUrl/api/$aktorId/forskuttering") {
            accept(ContentType.Application.Json)
            headers {
                append("Authorization", "Bearer accessToken")
                append("Nav-Consumer-Id", MDC.get("Nav-Consumer-Id"))
                append("Nav-Callid", MDC.get("Nav-Callid"))
            }
            parameter("orgnummer", orgnummer)
        }
    }
}

class ForskutteringRespons(val forskuttering: Forskuttering)

enum class Forskuttering {
    JA, NEI, UKJENT
}
