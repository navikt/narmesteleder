package no.nav.syfo.narmesteleder

import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.db.finnAlleNarmesteledereForSykmeldt
import no.nav.syfo.db.getAnsatte
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.arbeidsforhold.service.ArbeidsgiverService
import no.nav.syfo.narmesteleder.user.model.NarmesteLeder
import no.nav.syfo.pdl.model.toFormattedNameString
import no.nav.syfo.pdl.service.PdlPersonService
import java.util.function.Predicate

@KtorExperimentalAPI
class NarmesteLederService(
    private val database: DatabaseInterface,
    private val pdlPersonService: PdlPersonService,
    private val arbeidsgiverService: ArbeidsgiverService
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

    suspend fun getAnsatte(lederFnr: String, callId: String, status: String?, token: String): List<NarmesteLederRelasjon> {

        val nlFilter = when (status) {
            "ACTIVE" -> activeNLFilter()
            "INACTIVE" -> inactiveNLFilter()
            else -> allNlFilter()
        }

        val narmestelederRelasjoner = database.getAnsatte(lederFnr).filter { nlFilter.test(it) }
        if (narmestelederRelasjoner.isEmpty()) {
            log.info("Fant ingen narmesteleder relasjoner, with filter $status")
            return emptyList()
        }


        val ansatte = pdlPersonService.getPersoner(fnrs = narmestelederRelasjoner.map { it.fnr }, callId = callId)
        log.info("Got ${narmestelederRelasjoner.size} relasjoner from DB")

        return narmestelederRelasjoner.map { it.copy(navn = ansatte[it.fnr]?.navn?.toFormattedNameString()) }
    }

    private fun allNlFilter() = Predicate<NarmesteLederRelasjon> { true }

    private fun inactiveNLFilter() = Predicate<NarmesteLederRelasjon> { it.aktivTom != null }

    private fun activeNLFilter() = Predicate<NarmesteLederRelasjon> { it.aktivTom == null }

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
}
