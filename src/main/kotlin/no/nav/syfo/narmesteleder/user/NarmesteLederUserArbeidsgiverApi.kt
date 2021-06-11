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
import no.nav.syfo.application.metrics.DEAKTIVERT_AV_LEDER_COUNTER
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.NarmesteLederService
import no.nav.syfo.narmesteleder.leder.model.AnsattResponse
import no.nav.syfo.narmesteleder.leder.model.toAnsatt
import no.nav.syfo.narmesteleder.oppdatering.DeaktiverNarmesteLederService
import java.util.UUID

@KtorExperimentalAPI
fun Route.registrerNarmesteLederUserArbeidsgiverApi(
    deaktiverNarmesteLederService: DeaktiverNarmesteLederService,
    narmesteLederService: NarmesteLederService
) {
    get("/arbeidsgiver/ansatte") {
        val principal: JWTPrincipal = call.authentication.principal()!!
        val fnr = principal.payload.subject
        val callId = UUID.randomUUID()
        val status = call.parameters["status"]?.toUpperCase()
        val lederRelasjoner = narmesteLederService.getAnsatte(fnr, callId.toString(), status, "Bearer ${call.getToken()!!}")
        call.respond(AnsattResponse(lederRelasjoner.map { it.toAnsatt() }))
    }

    post("/arbeidsgiver/{orgnummer}/avkreft") {
        val principal: JWTPrincipal = call.authentication.principal()!!
        val fnrLeder = principal.payload.subject
        val token = call.getToken()!!
        val orgnummer = call.parameters["orgnummer"]?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("orgnummer mangler")
        val fnrSykmeldt: String = call.request.headers["Sykmeldt-Fnr"]?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Sykmeldt-Fnr mangler")

        val callId = UUID.randomUUID()
        deaktiverNarmesteLederService.deaktiverNarmesteLederForAnsatt(
            fnrLeder = fnrLeder,
            orgnummer = orgnummer,
            fnrSykmeldt = fnrSykmeldt,
            token = "Bearer $token",
            callId = callId
        )
        log.info("Nærmeste leder har deaktivert NL-kobling for orgnummer $orgnummer, $callId")
        DEAKTIVERT_AV_LEDER_COUNTER.inc()

        call.respond(HttpStatusCode.OK)
    }
}
