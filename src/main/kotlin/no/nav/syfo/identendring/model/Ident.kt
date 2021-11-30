package no.nav.syfo.identendring.model

data class Ident(
    val idnummer: String,
    val gjeldende: Boolean,
    val type: IdentType
)

enum class IdentType {
    FOLKEREGISTERIDENT, AKTORID, NPID
}
