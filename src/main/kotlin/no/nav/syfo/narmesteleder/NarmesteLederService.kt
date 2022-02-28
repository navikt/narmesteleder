package no.nav.syfo.narmesteleder

import kotlinx.coroutines.DelicateCoroutinesApi
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.db.finnAlleNarmesteledereForSykmeldt
import no.nav.syfo.db.getAnsatte
import no.nav.syfo.db.getNarmestelederRelasjon
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.user.model.NarmesteLeder
import no.nav.syfo.pdl.model.toFormattedNameString
import no.nav.syfo.pdl.service.PdlPersonService
import java.util.UUID

@DelicateCoroutinesApi
class NarmesteLederService(
    private val database: DatabaseInterface,
    private val pdlPersonService: PdlPersonService
) {
    suspend fun hentNarmesteledereMedNavn(sykmeldtFnr: String, callId: String): List<NarmesteLederRelasjon> {
        val narmesteLederRelasjoner = database.finnAlleNarmesteledereForSykmeldt(sykmeldtFnr)
        val nlFnrs = narmesteLederRelasjoner.map { it.narmesteLederFnr }
        if (nlFnrs.isEmpty()) {
            return emptyList()
        }
        val nlPersoner = pdlPersonService.getPersoner(fnrs = nlFnrs, callId = callId)

        return narmesteLederRelasjoner.map { it.copy(navn = nlPersoner[it.narmesteLederFnr]?.navn?.toFormattedNameString()) }
    }

    suspend fun getAnsatte(lederFnr: String, callId: String): List<NarmesteLederRelasjon> {
        val narmestelederRelasjoner = database.getAnsatte(lederFnr)
        if (narmestelederRelasjoner.isEmpty()) {
            log.info("Fant ingen narmesteleder relasjoner")
            return emptyList()
        }
        val ansatte = pdlPersonService.getPersoner(fnrs = narmestelederRelasjoner.map { it.fnr }, callId = callId)
        return narmestelederRelasjoner.map { it.copy(navn = ansatte[it.fnr]?.navn?.toFormattedNameString()) }
    }

    suspend fun hentNarmesteLedereForAnsatt(sykmeldtFnr: String, callId: String): List<NarmesteLeder> {
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
            navn = navn
        )
    }

    suspend fun getAnsatt(fnr: String, narmestelederId: UUID, callId: String): NarmesteLederRelasjon? {
        val ansatt = database.getNarmestelederRelasjon(narmestelederId, fnr)
        return when (ansatt) {
            null -> null
            else -> {
                val persons = pdlPersonService.getPersoner(fnrs = listOf(ansatt.fnr), callId = callId)
                return ansatt.copy(navn = persons[ansatt.fnr]?.navn?.toFormattedNameString())
            }
        }
    }
}
