package no.nav.syfo.forskuttering

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.log

fun Route.registrerForskutteringApi(database: DatabaseInterface) {
    get("/narmesteleder/arbeidsgiverForskutterer") {
        val request = call.request
        try {
            val queryParameters: Parameters = request.queryParameters
            val fnr = request.headers["fnr"]?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("Fnr mangler")
            val orgnummer: String = queryParameters["orgnummer"]?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("Orgnummer mangler")

            log.info("Mottatt forespørsel om forskuttering for fnr for orgnummer {}", orgnummer)
            val arbeidsgiverForskutterer = database.finnForskuttering(fnr, orgnummer)
            call.respond(arbeidsgiverForskutterer)
        } catch (e: IllegalArgumentException) {
            log.warn("Kan ikke hente forskuttering: {}", e.message)
            call.respond(HttpStatusCode.BadRequest, e.message!!)
        }
    }
}
