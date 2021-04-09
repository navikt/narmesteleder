package no.nav.syfo.narmesteleder.oppdatering.model

import java.time.OffsetDateTime

data class NlResponse(
    val orgnummer: String,
    val utbetalesLonn: Boolean?,
    val leder: Leder,
    val sykmeldt: Sykmeldt,
    val aktivFom: OffsetDateTime? = null,
    val aktivTom: OffsetDateTime? = null
)
