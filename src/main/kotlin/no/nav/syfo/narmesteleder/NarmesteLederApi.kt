package no.nav.syfo.narmesteleder

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.db.finnAktiveNarmestelederkoblinger
import no.nav.syfo.db.finnAlleNarmesteledereForSykmeldt
import no.nav.syfo.db.finnNarmestelederForSykmeldt
import no.nav.syfo.log
import org.slf4j.MDC

@KtorExperimentalAPI
fun Route.registrerNarmesteLederApi(
    database: DatabaseInterface,
    utvidetNarmesteLederService: UtvidetNarmesteLederService
) {
    get("/aktiveNarmestelederKoblinger") {
        val callId = MDC.get("Nav-Callid")
        try {
            val narmesteLederFnr: String = call.request.headers["narmesteLederFnr"]?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("NarmesteLederFnr mangler")

            call.respond(database.finnAktiveNarmestelederkoblinger(narmesteLederFnr))
        } catch (e: IllegalArgumentException) {
            log.warn("Kan ikke hente nærmeste leder: {}, {}", e.message, callId)
            call.respond(HttpStatusCode.BadRequest, e.message ?: "Kan ikke hente nærmeste leder")
        }
    }

    get("/narmestelederForSykmeldt") {
        val callId = MDC.get("Nav-Callid")
        try {
            val sykmeldtFnr: String = call.request.headers["sykmeldtFnr"]?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("sykmeldtFnr mangler")
            val orgnummer: String = call.request.queryParameters["orgnummer"]?.takeIf { it.isNotEmpty() }
                ?: throw NotImplementedError("Spørring uten orgnummer er ikke implementert")

            val narmesteLederRelasjon = database.finnNarmestelederForSykmeldt(sykmeldtFnr, orgnummer)
            call.respond(mapOf("narmesteLederRelasjon" to narmesteLederRelasjon))
        } catch (e: IllegalArgumentException) {
            log.warn("Kan ikke hente nærmeste leder da fnr mangler: {}, {}", e.message, callId)
            call.respond(HttpStatusCode.BadRequest, e.message!!)
        } catch (e: NotImplementedError) {
            log.info("Spørring uten orgnummer er ikke implementert {}, {}", e.message, callId)
            call.respond(HttpStatusCode.BadRequest, e.message!!)
        }
    }

    get("/alleNarmesteledereForSykmeldt") {
        val callId = MDC.get("Nav-Callid")
        try {
            val sykmeldtFnr: String = call.request.headers["sykmeldtFnr"]?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("sykmeldtFnr mangler")

            if (call.request.queryParameters["utvidet"] == "ja") {
                call.respond(
                    utvidetNarmesteLederService.hentNarmesteledereMedNavn(
                        sykmeldtFnr = sykmeldtFnr,
                        callId = callId
                    )
                )
            } else {
                val narmesteLederRelasjoner = database.finnAlleNarmesteledereForSykmeldt(sykmeldtFnr)
                call.respond(narmesteLederRelasjoner)
            }
        } catch (e: IllegalArgumentException) {
            log.warn("Kan ikke hente nærmeste ledere da fnr mangler: {}, {}", e.message, callId)
            call.respond(HttpStatusCode.BadRequest, e.message!!)
        }
    }
}
