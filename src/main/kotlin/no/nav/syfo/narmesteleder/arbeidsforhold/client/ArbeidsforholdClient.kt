package no.nav.syfo.narmesteleder.arbeidsforhold.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Arbeidsforhold
import java.time.LocalDate

class ArbeidsforholdClient(private val httpClient: HttpClient, private val url: String) {
    private val arbeidsforholdPath = "$url/api/v1/arbeidstaker/arbeidsforhold"
    private val navPersonident = "Nav-Personident"
    private val ansettelsesperiodeFomQueryParam = "ansettelsesperiodeFom"
    private val ansettelsesperiodeTomQueryParam = "ansettelsesperiodeTom"
    private val sporingsinformasjon = "sporingsinformasjon"

    suspend fun getArbeidsforhold(fnr: String, ansettelsesperiodeFom: LocalDate, token: String): List<Arbeidsforhold> {
        val iMorgen = LocalDate.now().plusDays(1).toString()
        return httpClient.get(
            "$arbeidsforholdPath?" +
                "$ansettelsesperiodeFomQueryParam=$ansettelsesperiodeFom&" +
                "$ansettelsesperiodeTomQueryParam=$iMorgen&" +
                "$sporingsinformasjon=false",
        ) {
            header(navPersonident, fnr)
            header(HttpHeaders.Authorization, token)
        }.body()
    }
}
