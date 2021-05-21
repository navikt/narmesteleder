package no.nav.syfo.narmesteleder.leder

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import no.nav.syfo.narmesteleder.UtvidetNarmesteLederService
import no.nav.syfo.narmesteleder.leder.model.AnsattResponse
import no.nav.syfo.narmesteleder.leder.model.toAnsatt
import java.util.UUID

fun Route.registerLederApi(
    narmesteLederService: UtvidetNarmesteLederService
) {
    get("leder/narmesteleder") {
        val principal: JWTPrincipal = call.authentication.principal()!!
        val fnr = principal.payload.subject
        val callId = UUID.randomUUID()
        val lederRelasjoner = narmesteLederService.getAnsatte(fnr, callId.toString())
        call.respond(AnsattResponse(lederRelasjoner.map { it.toAnsatt() }))
    }
}
