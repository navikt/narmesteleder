package no.nav.syfo.pdl.service

import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.log
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.ResponseData
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson

@KtorExperimentalAPI
class PdlPersonService(private val pdlClient: PdlClient, private val stsOidcClient: StsOidcClient) {
    companion object {
        const val AKTORID = "AKTORID"
    }

    suspend fun getPersoner(fnrs: List<String>, callId: String): Map<String, PdlPerson?> {
        val stsToken = stsOidcClient.oidcToken().access_token
        val pdlResponse = pdlClient.getPersoner(fnrs, stsToken)

        if (pdlResponse.errors != null) {
            pdlResponse.errors.forEach {
                log.error("PDL returnerte feilmelding: ${it.message}, ${it.extensions?.code}, $callId")
                it.extensions?.details?.let { details -> log.error("Type: ${details.type}, cause: ${details.cause}, policy: ${details.policy}, $callId") }
            }
        }
        if (pdlResponse.data.hentPersonBolk == null || pdlResponse.data.hentPersonBolk.isNullOrEmpty() ||
            pdlResponse.data.hentIdenterBolk == null || pdlResponse.data.hentIdenterBolk.isNullOrEmpty()
        ) {
            log.error("Fant ikke identer i PDL {}", callId)
            throw IllegalStateException("Fant ingen identer i PDL, skal ikke kunne skje!")
        }
        pdlResponse.data.hentPersonBolk.forEach {
            if (it.code != "ok") {
                log.warn("Mottok feilkode ${it.code} fra PDL for en eller flere personer, {}", callId)
            }
        }
        pdlResponse.data.hentIdenterBolk.forEach {
            if (it.code != "ok") {
                log.warn("Mottok feilkode ${it.code} fra PDL for en eller flere identer, {}", callId)
            }
        }

        return pdlResponse.data.toPdlPersonMap()
    }

    private fun ResponseData.toPdlPersonMap(): Map<String, PdlPerson?> {
        val identMap = hentIdenterBolk!!.map { it.ident to it.identer }.toMap()

        return hentPersonBolk!!.map {
            it.ident to
                if (it.person?.navn != null && it.person.navn.isNotEmpty()) {
                    PdlPerson(
                        navn = getNavn(it.person.navn.first()),
                        fnr = it.ident,
                        aktorId = identMap[it.ident]?.firstOrNull { ident -> ident.gruppe == AKTORID }?.ident
                    )
                } else {
                    null
                }
        }.toMap()
    }

    private fun getNavn(navn: no.nav.syfo.pdl.client.model.Navn): Navn =
        Navn(fornavn = navn.fornavn, mellomnavn = navn.mellomnavn, etternavn = navn.etternavn)
}
