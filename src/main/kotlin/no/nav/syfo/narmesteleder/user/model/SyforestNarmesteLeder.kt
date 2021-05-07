package no.nav.syfo.narmesteleder.user.model

import java.time.LocalDate

data class SyforestNarmesteLeder(
    val aktoerId: String,
    val navn: String,
    val epost: String,
    val mobil: String,
    val orgnummer: String,
    val organisasjonsnavn: String,
    val aktivTom: LocalDate?,
    val arbeidsgiverForskuttererLoenn: Boolean?
)
