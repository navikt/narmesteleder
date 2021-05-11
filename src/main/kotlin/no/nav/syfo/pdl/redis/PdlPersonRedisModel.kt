package no.nav.syfo.pdl.redis

import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson

data class PdlPersonRedisModel(
    val navn: NavnRedisModel,
    val fnr: String,
    val aktorId: String
)

data class NavnRedisModel(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String
)

fun PdlPerson.toPdlPersonRedisModel(): PdlPersonRedisModel {
    return PdlPersonRedisModel(
        navn = NavnRedisModel(fornavn = navn.fornavn, mellomnavn = navn.mellomnavn, etternavn = navn.etternavn),
        aktorId = aktorId!!,
        fnr = fnr!!
    )
}

fun PdlPersonRedisModel.toPdlPerson(): PdlPerson {
    return PdlPerson(
        navn = Navn(fornavn = navn.fornavn, mellomnavn = navn.mellomnavn, etternavn = navn.etternavn),
        aktorId = aktorId,
        fnr = fnr
    )
}
