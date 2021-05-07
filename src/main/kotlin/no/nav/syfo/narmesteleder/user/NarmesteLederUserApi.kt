package no.nav.syfo.narmesteleder.user

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.application.getToken
import no.nav.syfo.application.metrics.DEAKTIVERT_AV_ANSATT_COUNTER
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.SyforestNarmesteLederService
import no.nav.syfo.narmesteleder.UtvidetNarmesteLederService
import no.nav.syfo.narmesteleder.oppdatering.DeaktiverNarmesteLederService
import java.util.UUID

@KtorExperimentalAPI
fun Route.registrerNarmesteLederUserApi(
    deaktiverNarmesteLederService: DeaktiverNarmesteLederService,
    utvidetNarmesteLederService: UtvidetNarmesteLederService,
    syforestNarmesteLederService: SyforestNarmesteLederService
) {
    post("/{orgnummer}/avkreft") {
        val principal: JWTPrincipal = call.authentication.principal()!!
        val fnr = principal.payload.subject
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
        val principal: JWTPrincipal = call.authentication.principal()!!
        val fnr = principal.payload.subject
        val callId = UUID.randomUUID()

        call.respond(
            utvidetNarmesteLederService.hentNarmesteLedereForAnsatt(
                sykmeldtFnr = fnr,
                callId = callId.toString()
            )
        )
    }

    // tilbyr data på samme format som syforest og vil bli fjernet i fremtiden
    get("/syforest/narmesteledere") {
        val principal: JWTPrincipal = call.authentication.principal()!!
        val fnr = principal.payload.subject
        val callId = UUID.randomUUID()

        call.respond(
            syforestNarmesteLederService.hentAktiveNarmesteLedere(
                fnr = fnr,
                callId = callId.toString()
            )
        )
    }
}
