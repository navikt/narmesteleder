package no.nav.syfo.narmesteleder

import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.narmesteleder.organisasjon.client.OrganisasjonsinfoClient
import no.nav.syfo.narmesteleder.organisasjon.model.Organisasjonsinfo
import no.nav.syfo.narmesteleder.organisasjon.model.getName
import no.nav.syfo.narmesteleder.user.model.NarmesteLeder
import no.nav.syfo.narmesteleder.user.model.SyforestNarmesteLeder

@KtorExperimentalAPI
class SyforestNarmesteLederService(
    private val utvidetNarmesteLederService: UtvidetNarmesteLederService,
    private val organisasjonsinfoClient: OrganisasjonsinfoClient
) {
    suspend fun hentAktiveNarmesteLedere(fnr: String, callId: String): List<SyforestNarmesteLeder> {
        val narmesteLedere = utvidetNarmesteLederService.hentNarmesteLedereForAnsatt(
            sykmeldtFnr = fnr,
            callId = callId
        ).filter { it.aktivTom == null && it.navn != null }

        val orgnummere = narmesteLedere.map { it.orgnummer }.distinct()
        val organisasjoner = orgnummere.map { organisasjonsinfoClient.getOrginfo(it) }

        return narmesteLedere.map { it.toSyforestNarmesteLeder(organisasjoner) }
    }

    private fun NarmesteLeder.toSyforestNarmesteLeder(organisasjoner: List<Organisasjonsinfo>): SyforestNarmesteLeder {
        return SyforestNarmesteLeder(
            aktoerId = "", // brukes ikke
            navn = navn!!,
            epost = narmesteLederEpost,
            mobil = narmesteLederTelefonnummer,
            orgnummer = orgnummer,
            organisasjonsnavn = organisasjoner.firstOrNull { it.organisasjonsnummer == orgnummer }?.navn?.getName() ?: throw IllegalStateException("Fant ikke orgnavn for orgnummer $orgnummer"),
            aktivTom = aktivTom,
            arbeidsgiverForskuttererLoenn = arbeidsgiverForskutterer
        )
    }
}
