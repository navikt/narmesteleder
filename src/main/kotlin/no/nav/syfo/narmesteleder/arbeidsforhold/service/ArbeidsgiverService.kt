package no.nav.syfo.narmesteleder.arbeidsforhold.service

import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.arbeidsforhold.client.ArbeidsforholdClient
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Arbeidsforhold
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Arbeidsgiverinfo
import java.time.LocalDate

class ArbeidsgiverService(
    private val arbeidsforholdClient: ArbeidsforholdClient,
    private val stsOidcClient: StsOidcClient
) {
    suspend fun getArbeidsgivere(fnr: String, token: String?, forespurtAvAnsatt: Boolean): List<Arbeidsgiverinfo> {
        if (forespurtAvAnsatt && token == null) {
            log.error("Mangler token for henting av arbeidsgivere")
            throw IllegalStateException("Mangler token for henting av arbeidsgivere")
        }
        val stsToken = stsOidcClient.oidcToken()
        val ansettelsesperiodeFom = LocalDate.now().minusMonths(4)
        val arbeidsgivere = arbeidsforholdClient.getArbeidsforhold(
            fnr = fnr,
            ansettelsesperiodeFom = ansettelsesperiodeFom,
            token = if (forespurtAvAnsatt) { token!! } else { "Bearer ${stsToken.access_token}" },
            stsToken = stsToken.access_token
        )

        if (arbeidsgivere.isEmpty()) {
            return emptyList()
        }
        return arbeidsgivere.filter {
            it.arbeidsgiver.type == "Organisasjon"
        }.sortedWith(
            compareByDescending(nullsLast()) {
                it.ansettelsesperiode.periode.tom
            }
        ).distinctBy {
            it.arbeidsgiver.organisasjonsnummer
        }.map {
            toArbeidsgiverInfo(it)
        }
    }

    private fun toArbeidsgiverInfo(
        arbeidsforhold: Arbeidsforhold
    ): Arbeidsgiverinfo {
        return Arbeidsgiverinfo(
            orgnummer = arbeidsforhold.arbeidsgiver.organisasjonsnummer!!,
            juridiskOrgnummer = arbeidsforhold.opplysningspliktig.organisasjonsnummer!!,
            aktivtArbeidsforhold = arbeidsforhold.ansettelsesperiode.periode.tom == null ||
                !LocalDate.now().isAfter(arbeidsforhold.ansettelsesperiode.periode.tom)
        )
    }
}
