package no.nav.syfo.narmesteleder

import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.narmesteleder.organisasjon.client.OrganisasjonsinfoClient
import no.nav.syfo.narmesteleder.organisasjon.model.Navn
import no.nav.syfo.narmesteleder.organisasjon.model.Organisasjonsinfo
import no.nav.syfo.narmesteleder.user.model.NarmesteLeder
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@KtorExperimentalAPI
class SyforestNarmesteLederServiceTest : Spek({
    val utvidetNarmesteLederService = mockk<UtvidetNarmesteLederService>()
    val organisasjonsinfoClient = mockk<OrganisasjonsinfoClient>()
    val syforestNarmesteLederService = SyforestNarmesteLederService(utvidetNarmesteLederService, organisasjonsinfoClient)
    val callId = UUID.randomUUID()
    val fnr = "12345678910"

    beforeEachTest {
        clearMocks(organisasjonsinfoClient, utvidetNarmesteLederService)
    }

    describe("SyforestNarmesteLederService") {
        it("Henter aktive narmesteledere på riktig format") {
            coEvery { utvidetNarmesteLederService.hentNarmesteLedereForAnsatt(any(), any()) } returns listOf(
                NarmesteLeder(
                    orgnummer = "9955",
                    narmesteLederTelefonnummer = "90909090",
                    narmesteLederEpost = "epost@nav.no",
                    aktivFom = LocalDate.now().minusYears(1),
                    aktivTom = null,
                    arbeidsgiverForskutterer = true,
                    timestamp = OffsetDateTime.now().minusYears(1),
                    navn = "Leder Ledersen"
                )
            )
            coEvery { organisasjonsinfoClient.getOrginfo(any()) } returns Organisasjonsinfo("9955", Navn("OrgNavn", null, null, null, null, null))

            runBlocking {
                val syforestNarmesteLedere = syforestNarmesteLederService.hentAktiveNarmesteLedere(fnr, callId.toString())

                syforestNarmesteLedere.size shouldBeEqualTo 1
                syforestNarmesteLedere[0].aktoerId shouldBeEqualTo ""
                syforestNarmesteLedere[0].navn shouldBeEqualTo "Leder Ledersen"
                syforestNarmesteLedere[0].epost shouldBeEqualTo "epost@nav.no"
                syforestNarmesteLedere[0].mobil shouldBeEqualTo "90909090"
                syforestNarmesteLedere[0].orgnummer shouldBeEqualTo "9955"
                syforestNarmesteLedere[0].organisasjonsnavn shouldBeEqualTo "OrgNavn"
                syforestNarmesteLedere[0].aktivTom shouldBeEqualTo null
                syforestNarmesteLedere[0].arbeidsgiverForskuttererLoenn shouldBeEqualTo true
            }
        }
        it("Henter ikke inaktive narmesteledere") {
            coEvery { utvidetNarmesteLederService.hentNarmesteLedereForAnsatt(any(), any()) } returns listOf(
                NarmesteLeder(
                    orgnummer = "9955",
                    narmesteLederTelefonnummer = "90909090",
                    narmesteLederEpost = "epost@nav.no",
                    aktivFom = LocalDate.now().minusYears(1),
                    aktivTom = LocalDate.now(),
                    arbeidsgiverForskutterer = true,
                    timestamp = OffsetDateTime.now().minusYears(1),
                    navn = "Leder Ledersen"
                )
            )
            coEvery { organisasjonsinfoClient.getOrginfo(any()) } returns Organisasjonsinfo("9955", Navn("OrgNavn", null, null, null, null, null))

            runBlocking {
                val syforestNarmesteLedere = syforestNarmesteLederService.hentAktiveNarmesteLedere(fnr, callId.toString())

                syforestNarmesteLedere.size shouldBeEqualTo 0
            }
        }
        it("Henter ikke narmesteledere som vi ikke fant navn for") {
            coEvery { utvidetNarmesteLederService.hentNarmesteLedereForAnsatt(any(), any()) } returns listOf(
                NarmesteLeder(
                    orgnummer = "9955",
                    narmesteLederTelefonnummer = "90909090",
                    narmesteLederEpost = "epost@nav.no",
                    aktivFom = LocalDate.now().minusYears(1),
                    aktivTom = null,
                    arbeidsgiverForskutterer = true,
                    timestamp = OffsetDateTime.now().minusYears(1),
                    navn = null
                )
            )
            coEvery { organisasjonsinfoClient.getOrginfo(any()) } returns Organisasjonsinfo("9955", Navn("OrgNavn", null, null, null, null, null))

            runBlocking {
                val syforestNarmesteLedere = syforestNarmesteLederService.hentAktiveNarmesteLedere(fnr, callId.toString())

                syforestNarmesteLedere.size shouldBeEqualTo 0
            }
        }
        it("Henter aktive narmesteledere på riktig format hvis flere aktive") {
            coEvery { utvidetNarmesteLederService.hentNarmesteLedereForAnsatt(any(), any()) } returns listOf(
                NarmesteLeder(
                    orgnummer = "9955",
                    narmesteLederTelefonnummer = "90909090",
                    narmesteLederEpost = "epost@nav.no",
                    aktivFom = LocalDate.now().minusYears(1),
                    aktivTom = null,
                    arbeidsgiverForskutterer = true,
                    timestamp = OffsetDateTime.now().minusYears(1),
                    navn = "Leder Ledersen"
                ),
                NarmesteLeder(
                    orgnummer = "9955",
                    narmesteLederTelefonnummer = "80808080",
                    narmesteLederEpost = "epost2@nav.no",
                    aktivFom = LocalDate.now().minusYears(1),
                    aktivTom = null,
                    arbeidsgiverForskutterer = true,
                    timestamp = OffsetDateTime.now().minusYears(1),
                    navn = "Annen-Leder Ledersen"
                ),
                NarmesteLeder(
                    orgnummer = "8585",
                    narmesteLederTelefonnummer = "99999999",
                    narmesteLederEpost = "epost@banken.no",
                    aktivFom = LocalDate.now().minusYears(1),
                    aktivTom = null,
                    arbeidsgiverForskutterer = true,
                    timestamp = OffsetDateTime.now().minusYears(1),
                    navn = "Sjef Sjefesen"
                )
            )
            coEvery { organisasjonsinfoClient.getOrginfo(eq("9955")) } returns Organisasjonsinfo("9955", Navn("OrgNavn", null, null, null, null, null))
            coEvery { organisasjonsinfoClient.getOrginfo(eq("8585")) } returns Organisasjonsinfo("8585", Navn("OrgNavn2", null, null, null, null, null))

            runBlocking {
                val syforestNarmesteLedere = syforestNarmesteLederService.hentAktiveNarmesteLedere(fnr, callId.toString())

                syforestNarmesteLedere.size shouldBeEqualTo 3
                val leder1 = syforestNarmesteLedere.find { it.navn == "Leder Ledersen" }
                leder1?.orgnummer shouldBeEqualTo "9955"
                leder1?.organisasjonsnavn shouldBeEqualTo "OrgNavn"
                val leder2 = syforestNarmesteLedere.find { it.navn == "Annen-Leder Ledersen" }
                leder2?.orgnummer shouldBeEqualTo "9955"
                leder2?.organisasjonsnavn shouldBeEqualTo "OrgNavn"
                val leder3 = syforestNarmesteLedere.find { it.navn == "Sjef Sjefesen" }
                leder3?.orgnummer shouldBeEqualTo "8585"
                leder3?.organisasjonsnavn shouldBeEqualTo "OrgNavn2"

                coVerify(exactly = 2) { organisasjonsinfoClient.getOrginfo(any()) }
            }
        }
    }
})
