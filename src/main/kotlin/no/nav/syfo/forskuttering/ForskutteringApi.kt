package no.nav.syfo.forskuttering

import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.db.finnForskuttering
import no.nav.syfo.log
import org.slf4j.MDC

fun Route.registrerForskutteringApi(database: DatabaseInterface) {
    get("/arbeidsgiver/forskutterer") {
        val request = call.request
        val callId = MDC.get("Nav-Callid")
        try {
            val queryParameters: Parameters = request.queryParameters
            val fnr =
                request.headers["Sykmeldt-Fnr"]?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalArgumentException("Sykmeldt-Fnr mangler")
            val orgnummer: String =
                queryParameters["orgnummer"]?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalArgumentException("Orgnummer mangler")

            log.info(
                "Mottatt forespørsel om forskuttering for fnr for orgnummer {}, {}",
                orgnummer,
                callId
            )
            val arbeidsgiverForskutterer = database.finnForskuttering(fnr, orgnummer)
            call.respond(arbeidsgiverForskutterer)
        } catch (e: IllegalArgumentException) {
            log.warn("Kan ikke hente forskuttering: {}, {}", e.message, callId)
            call.respond(HttpStatusCode.BadRequest, e.message!!)
        }
    }
}
