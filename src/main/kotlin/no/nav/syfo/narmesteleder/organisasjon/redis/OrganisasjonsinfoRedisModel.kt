package no.nav.syfo.narmesteleder.organisasjon.redis

import no.nav.syfo.narmesteleder.organisasjon.model.Navn
import no.nav.syfo.narmesteleder.organisasjon.model.Organisasjonsinfo

data class OrganisasjonsinfoRedisModel(
    val organisasjonsnummer: String,
    val navn: NavnRedisModel
)

data class NavnRedisModel(
    val navnelinje1: String?,
    val navnelinje2: String?,
    val navnelinje3: String?,
    val navnelinje4: String?,
    val navnelinje5: String?,
    val redigertnavn: String?
)

fun Organisasjonsinfo.toOrganisasjonsinfoRedisModel(): OrganisasjonsinfoRedisModel {
    return OrganisasjonsinfoRedisModel(
        organisasjonsnummer = organisasjonsnummer,
        navn = NavnRedisModel(
            navnelinje1 = navn.navnelinje1,
            navnelinje2 = navn.navnelinje2,
            navnelinje3 = navn.navnelinje3,
            navnelinje4 = navn.navnelinje4,
            navnelinje5 = navn.navnelinje5,
            redigertnavn = navn.redigertnavn
        )
    )
}

fun OrganisasjonsinfoRedisModel.toOrganisasjonsinfo(): Organisasjonsinfo {
    return Organisasjonsinfo(
        organisasjonsnummer = organisasjonsnummer,
        navn = Navn(
            navnelinje1 = navn.navnelinje1,
            navnelinje2 = navn.navnelinje2,
            navnelinje3 = navn.navnelinje3,
            navnelinje4 = navn.navnelinje4,
            navnelinje5 = navn.navnelinje5,
            redigertnavn = navn.redigertnavn
        )
    )
}
