package no.nav.syfo.pdl.service

import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.log
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.model.Navn

@KtorExperimentalAPI
class PdlPersonService(private val pdlClient: PdlClient, private val stsOidcClient: StsOidcClient) {

    suspend fun getPersonnavn(fnrs: List<String>, callId: String): Map<String, Navn?> {
        val stsToken = stsOidcClient.oidcToken().access_token
        val pdlResponse = pdlClient.getPersoner(fnrs, stsToken)

        if (pdlResponse.errors != null) {
            pdlResponse.errors.forEach {
                log.error("PDL returnerte feilmelding: ${it.message}, ${it.extensions?.code}, $callId")
                it.extensions?.details?.let { details -> log.error("Type: ${details.type}, cause: ${details.cause}, policy: ${details.policy}, $callId") }
            }
        }
        if (pdlResponse.data.hentPersonBolk == null || pdlResponse.data.hentPersonBolk.isNullOrEmpty()) {
            log.error("Fant ikke identer i PDL {}", callId)
            throw IllegalStateException("Fant ingen identer i PDL, skal ikke kunne skje!")
        }
        pdlResponse.data.hentPersonBolk.forEach {
            if (it.code != "ok") {
                log.warn("Mottok feilkode ${it.code} fra PDL for en eller flere identer, {}", callId)
            }
        }
        return pdlResponse.data.hentPersonBolk.map {
            it.ident to getNavn(it.person?.navn?.firstOrNull())
        }.toMap()
    }

    private fun getNavn(navn: no.nav.syfo.pdl.client.model.Navn?): Navn? {
        return if (navn == null) {
            null
        } else {
            Navn(fornavn = navn.fornavn, mellomnavn = navn.mellomnavn, etternavn = navn.etternavn)
        }
    }
}
