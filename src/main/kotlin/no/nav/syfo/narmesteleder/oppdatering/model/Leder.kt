package no.nav.syfo.narmesteleder.oppdatering.model

data class Leder(
    val fnr: String,
    val mobil: String,
    val epost: String,
    val fornavn: String?,
    val etternavn: String?,
)
