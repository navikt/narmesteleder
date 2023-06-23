package no.nav.syfo.narmesteleder

import kotlinx.coroutines.DelicateCoroutinesApi
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.db.finnAlleNarmesteledereForSykmeldt
import no.nav.syfo.narmesteleder.user.model.NarmesteLeder
import no.nav.syfo.pdl.model.toFormattedNameString
import no.nav.syfo.pdl.service.PdlPersonService

@DelicateCoroutinesApi
class NarmesteLederService(
    private val database: DatabaseInterface,
    private val pdlPersonService: PdlPersonService,
) {
    suspend fun hentNarmesteledereMedNavn(
        sykmeldtFnr: String,
        callId: String
    ): List<NarmesteLederRelasjon> {
        val narmesteLederRelasjoner = database.finnAlleNarmesteledereForSykmeldt(sykmeldtFnr)
        val nlFnrs = narmesteLederRelasjoner.map { it.narmesteLederFnr }
        if (nlFnrs.isEmpty()) {
            return emptyList()
        }
        val nlPersoner = pdlPersonService.getPersoner(fnrs = nlFnrs, callId = callId)

        return narmesteLederRelasjoner.map {
            it.copy(navn = nlPersoner[it.narmesteLederFnr]?.navn?.toFormattedNameString())
        }
    }

    suspend fun hentNarmesteLedereForAnsatt(
        sykmeldtFnr: String,
        callId: String
    ): List<NarmesteLeder> {
        return hentNarmesteledereMedNavn(sykmeldtFnr, callId).map { it.tilNarmesteLeder() }
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
