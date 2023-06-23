package no.nav.syfo.narmesteleder.oppdatering.kafka.model

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

const val NY_LEDER = "NY_LEDER"
const val DEAKTIVERT_ARBEIDSTAKER = "DEAKTIVERT_ARBEIDSTAKER"
const val DEAKTIVERT_ARBEIDSTAKER_INNSENDT_SYKMELDING =
    "DEAKTIVERT_ARBEIDSTAKER_INNSENDT_SYKMELDING"
const val DEAKTIVERT_LEDER = "DEAKTIVERT_LEDER"
const val DEAKTIVERT_ARBEIDSFORHOLD = "DEAKTIVERT_ARBEIDSFORHOLD"
const val DEAKTIVERT_NY_LEDER = "DEAKTIVERT_NY_LEDER"
const val IDENTENDRING = "IDENTENDRING"

data class NarmesteLederLeesah(
    val narmesteLederId: UUID,
    val fnr: String,
    val orgnummer: String,
    val narmesteLederFnr: String,
    val narmesteLederTelefonnummer: String,
    val narmesteLederEpost: String,
    val aktivFom: LocalDate,
    val aktivTom: LocalDate?,
    val arbeidsgiverForskutterer: Boolean?,
    val timestamp: OffsetDateTime,
    val status: String? = null,
)
