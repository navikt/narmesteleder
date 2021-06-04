package no.nav.syfo.narmesteleder.arbeidsforhold.service

import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.narmesteleder.arbeidsforhold.client.ArbeidsforholdClient
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Arbeidsforhold
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Arbeidsgiverinfo
import java.time.LocalDate

@KtorExperimentalAPI
class ArbeidsgiverService(
    private val arbeidsforholdClient: ArbeidsforholdClient,
    private val stsOidcClient: StsOidcClient
) {
    suspend fun getArbeidsgivere(fnr: String, token: String, forespurtAvAnsatt: Boolean): List<Arbeidsgiverinfo> {
        val stsToken = stsOidcClient.oidcToken()
        val ansettelsesperiodeFom = LocalDate.now().minusMonths(4)
        val arbeidsgivere = arbeidsforholdClient.getArbeidsforhold(
            fnr = fnr,
            ansettelsesperiodeFom = ansettelsesperiodeFom,
            token = if (forespurtAvAnsatt) { token } else { "Bearer ${stsToken.access_token}" },
            stsToken = stsToken.access_token
        )

        if (arbeidsgivere.isEmpty()) {
            return emptyList()
        }
        val arbeidsgiverList = ArrayList<Arbeidsgiverinfo>()
        arbeidsgivere.filter {
            it.arbeidsgiver.type == "Organisasjon"
        }.distinctBy {
            it.arbeidsgiver.organisasjonsnummer
        }.forEach { arbeidsforhold ->
            addArbeidsinfo(arbeidsgiverList, arbeidsforhold)
        }
        return arbeidsgiverList
    }

    private fun addArbeidsinfo(
        arbeidsgiverList: ArrayList<Arbeidsgiverinfo>,
        arbeidsforhold: Arbeidsforhold
    ) {
        arbeidsgiverList.add(
            Arbeidsgiverinfo(
                orgnummer = arbeidsforhold.arbeidsgiver.organisasjonsnummer!!,
                juridiskOrgnummer = arbeidsforhold.opplysningspliktig.organisasjonsnummer!!,
                aktivtArbeidsforhold = arbeidsforhold.ansettelsesperiode.periode.tom == null
            )
        )
    }
}
