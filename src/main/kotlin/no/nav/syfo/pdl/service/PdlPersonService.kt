package no.nav.syfo.pdl.service

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import no.nav.syfo.application.client.AccessTokenClientV2
import no.nav.syfo.log
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.client.model.ResponseData
import no.nav.syfo.pdl.error.InactiveIdentException
import no.nav.syfo.pdl.error.PersonNotFoundException
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.redis.PdlPersonRedisService
import no.nav.syfo.pdl.redis.toPdlPerson
import no.nav.syfo.pdl.redis.toPdlPersonRedisModel

@DelicateCoroutinesApi
class PdlPersonService(
    private val pdlClient: PdlClient,
    private val accessTokenClientV2: AccessTokenClientV2,
    private val pdlPersonRedisService: PdlPersonRedisService,
    private val pdlScope: String,
) {
    companion object {
        const val AKTORID = "AKTORID"
    }

    suspend fun getPersoner(fnrs: List<String>, callId: String): Map<String, PdlPerson?> {
        val personerFraRedis = getPersonerFromRedis(fnrs)
        val fnrsManglerIRedis = fnrs.filter { !personerFraRedis.containsKey(it) }

        if (fnrsManglerIRedis.isNotEmpty()) {
            val accessToken = accessTokenClientV2.getAccessTokenV2(pdlScope)

            val pdlResponse = getPersonsFromPdl(fnrsManglerIRedis, accessToken)

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

            val pdlPersonMap = pdlResponse.data.toPdlPersonMap()
            pdlPersonMap.forEach {
                if (it.value != null) {
                    pdlPersonRedisService.updatePerson(it.value!!.toPdlPersonRedisModel(), it.key)
                }
            }
            return personerFraRedis.plus(pdlPersonMap)
        }
        return personerFraRedis
    }

    suspend fun erIdentAktiv(fnr: String): Boolean {
        val accessToken = accessTokenClientV2.getAccessTokenV2(pdlScope)
        val pdlResponse = getPersonsFromPdl(listOf(fnr), accessToken)

        if (pdlResponse.errors != null) {
            pdlResponse.errors.forEach {
                log.warn("PDL kastet error: {} ", it)
            }
        }
        if (pdlResponse.data.hentIdenterBolk == null || pdlResponse.data.hentIdenterBolk.isNullOrEmpty()) {
            log.warn("Fant ikke person i PDL")
            throw PersonNotFoundException("Fant ikke person i PDL")
        }
        // Spørring mot PDL er satt opp til å bare returnere aktive identer, og denne sjekken forutsetter dette
        if (!pdlResponse.data.toPdlPersonMap().containsKey(fnr)) {
            throw InactiveIdentException("PDL svarer men ident er ikke aktiv")
        }

        return true
    }

    private suspend fun getPersonsFromPdl(
        fnrs: List<String>,
        stsToken: String,
    ): GetPersonResponse {
        val listFnrs = fnrs.chunked(100).map {
            GlobalScope.async(context = Dispatchers.IO) {
                pdlClient.getPersoner(it, stsToken)
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
                        aktorId = identMap[it.ident]?.firstOrNull { ident -> ident.gruppe == AKTORID }?.ident,
                    )
                } else {
                    null
                }
        }
    }

    private fun getNavn(navn: no.nav.syfo.pdl.client.model.Navn): Navn =
        Navn(fornavn = navn.fornavn, mellomnavn = navn.mellomnavn, etternavn = navn.etternavn)

    private fun getPersonerFromRedis(fnrs: List<String>): Map<String, PdlPerson> {
        return pdlPersonRedisService.getPerson(fnrs).filterValues { it != null }.mapValues { it.value!!.toPdlPerson() }
    }
}
