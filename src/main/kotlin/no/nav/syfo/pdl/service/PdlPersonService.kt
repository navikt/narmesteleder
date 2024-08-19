package no.nav.syfo.pdl.service

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import no.nav.syfo.application.client.AccessTokenClientV2
import no.nav.syfo.log
import no.nav.syfo.objectMapper
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.client.model.ResponseData
import no.nav.syfo.pdl.error.InactiveIdentException
import no.nav.syfo.pdl.error.PersonNotFoundException
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.securelog

@DelicateCoroutinesApi
class PdlPersonService(
    private val pdlClient: PdlClient,
    private val accessTokenClientV2: AccessTokenClientV2,
    private val pdlScope: String
) {

    companion object {
        const val AKTORID = "AKTORID"
    }

    suspend fun getPersonerByIdenter(identer: List<String>): Collection<PdlPerson?> {
        return getPersonsFromPdl(identer).data.toPdlPersonMap().values
    }

    suspend fun getPersoner(fnrs: List<String>, callId: String): Map<String, PdlPerson?> {

        val pdlResponse = getPersonsFromPdl(fnrs)

        if (pdlResponse.errors != null) {
            pdlResponse.errors.forEach {
                log.error(
                    "PDL returnerte feilmelding: ${it.message}, ${it.extensions?.code}, $callId"
                )
                it.extensions?.details?.let { details ->
                    log.error(
                        "Type: ${details.type}, cause: ${details.cause}, policy: ${details.policy}, $callId"
                    )
                }
            }
        }
        if (
            pdlResponse.data.hentPersonBolk == null ||
                pdlResponse.data.hentPersonBolk.isNullOrEmpty() ||
                pdlResponse.data.hentIdenterBolk == null ||
                pdlResponse.data.hentIdenterBolk.isNullOrEmpty()
        ) {
            log.error("Fant ikke identer i PDL {}", callId)
            throw IllegalStateException("Fant ingen identer i PDL, skal ikke kunne skje!")
        }
        pdlResponse.data.hentPersonBolk.forEach {
            if (it.code != "ok") {
                log.warn(
                    "Mottok feilkode ${it.code} fra PDL for en eller flere personer, {}",
                    callId
                )
            }
        }
        pdlResponse.data.hentIdenterBolk.forEach {
            if (it.code != "ok") {
                log.warn(
                    "Mottok feilkode ${it.code} fra PDL for en eller flere identer, {}",
                    callId
                )
            }
        }

        return pdlResponse.data.toPdlPersonMap()
    }

    suspend fun erIdentAktiv(fnr: String): Boolean {
        val pdlResponse = getPersonsFromPdl(listOf(fnr))

        if (pdlResponse.errors != null) {
            pdlResponse.errors.forEach { log.warn("PDL kastet error: {} ", it) }
        }
        if (
            pdlResponse.data.hentIdenterBolk == null ||
                pdlResponse.data.hentIdenterBolk.isNullOrEmpty()
        ) {
            log.warn("Fant ikke person i PDL")
            throw PersonNotFoundException("Fant ikke person i PDL")
        }
        // Spørring mot PDL er satt opp til å bare returnere aktive identer, og denne sjekken
        // forutsetter dette
        if (!pdlResponse.data.toPdlPersonMap().containsKey(fnr)) {
            throw InactiveIdentException("PDL svarer men ident er ikke aktiv")
        }

        return true
    }

    private suspend fun getPersonsFromPdl(ider: List<String>): GetPersonResponse {

        val accessToken = accessTokenClientV2.getAccessTokenV2(pdlScope)
        val listFnrs =
            ider.chunked(100).map {
                GlobalScope.async(context = Dispatchers.IO) {
                    pdlClient.getPersoner(it, accessToken).also {
                        securelog.info("GetPersonResponse: ${objectMapper.writeValueAsString(it)}")
                    }
                }
            }

        val responses = listFnrs.awaitAll().map { it }
        val identer = responses.mapNotNull { it.data.hentIdenterBolk }.flatten()
        val personBolk = responses.mapNotNull { it.data.hentPersonBolk }.flatten()
        val errors = responses.mapNotNull { it.errors }.flatten()

        return GetPersonResponse(
            ResponseData(
                hentPersonBolk = personBolk,
                hentIdenterBolk = identer,
            ),
            errors = errors,
        )
    }

    private fun ResponseData.toPdlPersonMap(): Map<String, PdlPerson?> {
        val identMap = hentIdenterBolk!!.associate { it.ident to it.identer }

        return hentPersonBolk!!.associate {
            it.ident to
                if (it.person?.navn != null && it.person.navn.isNotEmpty()) {
                    PdlPerson(
                        navn = getNavn(it.person.navn.first()),
                        fnr = it.ident,
                        aktorId =
                            identMap[it.ident]
                                ?.firstOrNull { ident -> ident.gruppe == AKTORID }
                                ?.ident,
                    )
                } else {
                    null
                }
        }
    }

    private fun getNavn(navn: no.nav.syfo.pdl.client.model.Navn): Navn =
        Navn(fornavn = navn.fornavn, mellomnavn = navn.mellomnavn, etternavn = navn.etternavn)
}
