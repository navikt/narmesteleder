package no.nav.syfo.narmesteleder.organisasjon.client

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import no.nav.syfo.narmesteleder.organisasjon.model.Organisasjonsinfo

class OrganisasjonsinfoClient(
    private val httpClient: HttpClient,
    private val basePath: String,
    private val apiKey: String
) {
    suspend fun getOrginfo(orgNummer: String): Organisasjonsinfo {
        return httpClient.get("$basePath/ereg/api/v1/organisasjon/$orgNummer/noekkelinfo") {
            header("x-nav-apikey", apiKey)
        }
    }
}
