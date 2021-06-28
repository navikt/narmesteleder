package no.nav.syfo.narmesteleder.user

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.application.BrukerPrincipal
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.NarmesteLederService
import no.nav.syfo.narmesteleder.leder.model.AnsattResponse
import no.nav.syfo.narmesteleder.leder.model.toAnsatt
import java.util.UUID

@KtorExperimentalAPI
fun Route.registrerNarmesteLederUserArbeidsgiverApiV2(
    narmesteLederService: NarmesteLederService
) {
    get("/arbeidsgiver/v2/ansatte") {
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr
        log.info("Fnr: $fnr")
        val callId = UUID.randomUUID()
        val lederRelasjoner = narmesteLederService.getAnsatte(fnr, callId.toString())
        call.respond(AnsattResponse(lederRelasjoner.map { it.toAnsatt() }))
    }
}
