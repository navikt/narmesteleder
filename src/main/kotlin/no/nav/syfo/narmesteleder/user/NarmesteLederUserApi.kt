package no.nav.syfo.narmesteleder.user

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import no.nav.syfo.application.BrukerPrincipal
import no.nav.syfo.application.getToken
import no.nav.syfo.application.metrics.DEAKTIVERT_AV_ANSATT_COUNTER
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.NarmesteLederService
import no.nav.syfo.narmesteleder.oppdatering.DeaktiverNarmesteLederService
import java.util.UUID

fun Route.registrerNarmesteLederUserApi(
    deaktiverNarmesteLederService: DeaktiverNarmesteLederService,
    utvidetNarmesteLederService: NarmesteLederService
) {
    post("/{orgnummer}/avkreft") {
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr
        val token = call.getToken()!!
        val orgnummer = call.parameters["orgnummer"]?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("orgnummer mangler")

        val callId = UUID.randomUUID()
        deaktiverNarmesteLederService.deaktiverNarmesteLeder(
            orgnummer = orgnummer,
            fnrSykmeldt = fnr,
            token = "Bearer $token",
            callId = callId
        )
        log.info("Den ansatte har deaktivert NL-kobling for orgnummer $orgnummer, $callId")
        DEAKTIVERT_AV_ANSATT_COUNTER.inc()

        call.respond(HttpStatusCode.OK)
    }

    get("/user/sykmeldt/narmesteledere") {
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr
        val callId = UUID.randomUUID()

        call.respond(
            utvidetNarmesteLederService.hentNarmesteLedereForAnsatt(
                sykmeldtFnr = fnr,
                callId = callId.toString()
            )
        )
    }
}
