package no.nav.syfo.narmesteleder.organisasjon.client

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.organisasjon.model.Organisasjonsinfo
import no.nav.syfo.narmesteleder.organisasjon.redis.OrganisasjonsinfoRedisService
import no.nav.syfo.narmesteleder.organisasjon.redis.toOrganisasjonsinfo
import no.nav.syfo.narmesteleder.organisasjon.redis.toOrganisasjonsinfoRedisModel

class OrganisasjonsinfoClient(
    private val httpClient: HttpClient,
    private val basePath: String,
    private val apiKey: String,
    private val organisasjonsinfoRedisService: OrganisasjonsinfoRedisService
) {
    suspend fun getOrginfo(orgNummer: String): Organisasjonsinfo {
        val organisasjonsinfoFraRedis = getOrganisasjonsinfoFromRedis(orgNummer)
        if (organisasjonsinfoFraRedis != null) {
            log.info("Traff cache")
            return organisasjonsinfoFraRedis
        }
        log.info("Henter fra ereg")
        val organisasjonsinfo = httpClient.get<Organisasjonsinfo>("$basePath/ereg/api/v1/organisasjon/$orgNummer/noekkelinfo") {
            header("x-nav-apikey", apiKey)
        }
        organisasjonsinfoRedisService.updateOrganisasjonsinfo(orgNummer, organisasjonsinfo.toOrganisasjonsinfoRedisModel())
        return organisasjonsinfo
    }

    private fun getOrganisasjonsinfoFromRedis(orgnummer: String): Organisasjonsinfo? {
        return organisasjonsinfoRedisService.getOrganisasjonsinfo(orgnummer)?.toOrganisasjonsinfo()
    }
}
