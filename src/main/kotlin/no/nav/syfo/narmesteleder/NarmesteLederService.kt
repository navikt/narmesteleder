package no.nav.syfo.narmesteleder

import kotlinx.coroutines.DelicateCoroutinesApi
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.db.finnAlleNarmesteledereForSykmeldt
import no.nav.syfo.narmesteleder.user.model.NarmesteLeder

@DelicateCoroutinesApi
class NarmesteLederService(
    private val database: DatabaseInterface,
) {
    suspend fun hentNarmesteledereMedNavn(sykmeldtFnr: String): List<NarmesteLederRelasjon> {
        return database.finnAlleNarmesteledereForSykmeldt(sykmeldtFnr)
    }

    suspend fun hentNarmesteLedereForAnsatt(sykmeldtFnr: String): List<NarmesteLeder> {
        return hentNarmesteledereMedNavn(sykmeldtFnr).map { it.tilNarmesteLeder() }
    }

    private fun NarmesteLederRelasjon.tilNarmesteLeder(): NarmesteLeder {
        return NarmesteLeder(
            orgnummer = orgnummer,
            narmesteLederTelefonnummer = narmesteLederTelefonnummer,
            narmesteLederEpost = narmesteLederEpost,
            aktivFom = aktivFom,
            aktivTom = aktivTom,
            arbeidsgiverForskutterer = arbeidsgiverForskutterer,
            timestamp = timestamp,
            navn = navn,
        )
    }
}
