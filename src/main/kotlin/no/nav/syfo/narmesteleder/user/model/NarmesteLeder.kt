package no.nav.syfo.narmesteleder.user.model

import java.time.LocalDate
import java.time.OffsetDateTime

data class NarmesteLeder(
    val orgnummer: String,
    val narmesteLederTelefonnummer: String,
    val narmesteLederEpost: String,
    val aktivFom: LocalDate,
    val aktivTom: LocalDate?,
    val arbeidsgiverForskutterer: Boolean?,
    val timestamp: OffsetDateTime,
    val navn: String?
)
