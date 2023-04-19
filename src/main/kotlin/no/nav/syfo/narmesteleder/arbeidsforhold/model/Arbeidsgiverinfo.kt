package no.nav.syfo.narmesteleder.arbeidsforhold.model

data class Arbeidsgiverinfo(
    val orgnummer: String,
    val juridiskOrgnummer: String,
    val aktivtArbeidsforhold: Boolean,
)
