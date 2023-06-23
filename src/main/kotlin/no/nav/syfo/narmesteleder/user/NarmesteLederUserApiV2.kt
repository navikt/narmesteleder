package no.nav.syfo.narmesteleder.user

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authentication
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.util.UUID
import kotlinx.coroutines.DelicateCoroutinesApi
import no.nav.syfo.application.BrukerPrincipal
import no.nav.syfo.application.metrics.DEAKTIVERT_AV_ANSATT_COUNTER
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.NarmesteLederService
import no.nav.syfo.narmesteleder.oppdatering.DeaktiverNarmesteLederService

@DelicateCoroutinesApi
fun Route.registrerNarmesteLederUserApiV2(
    deaktiverNarmesteLederService: DeaktiverNarmesteLederService,
    utvidetNarmesteLederService: NarmesteLederService,
) {
    post("/v2/{orgnummer}/avkreft") {
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr
        val orgnummer =
            call.parameters["orgnummer"]?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("orgnummer mangler")

        val callId = UUID.randomUUID()
        deaktiverNarmesteLederService.deaktiverNarmesteLeder(
            orgnummer = orgnummer,
            fnrSykmeldt = fnr,
        )
        log.info("Den ansatte har deaktivert NL-kobling for orgnummer $orgnummer, $callId (tokenx)")
        DEAKTIVERT_AV_ANSATT_COUNTER.inc()

        call.respond(HttpStatusCode.OK)
    }

    get("/user/v2/sykmeldt/narmesteledere") {
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr
        val callId = UUID.randomUUID()

        call.respond(
            utvidetNarmesteLederService.hentNarmesteLedereForAnsatt(
                sykmeldtFnr = fnr,
                callId = callId.toString(),
            ),
        )
    }
}
