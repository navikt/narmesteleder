package no.nav.syfo.narmesteleder.organisasjon.model

data class Navn(
    val navnelinje1: String?,
    val navnelinje2: String?,
    val navnelinje3: String?,
    val navnelinje4: String?,
    val navnelinje5: String?,
    val redigertnavn: String?
)

fun Navn.getName(): String {
    val builder = StringBuilder()
    if (!navnelinje1.isNullOrBlank()) {
        builder.appendLine(navnelinje1)
    }
    if (!navnelinje2.isNullOrBlank()) {
        builder.appendLine(navnelinje2)
    }
    if (!navnelinje3.isNullOrBlank()) {
        builder.appendLine(navnelinje3)
    }
    if (!navnelinje4.isNullOrBlank()) {
        builder.appendLine(navnelinje4)
    }
    if (!navnelinje5.isNullOrBlank()) {
        builder.appendLine(navnelinje5)
    }
    return builder.lineSequence().filter {
        it.isNotBlank()
    }.joinToString(separator = ",")
}
