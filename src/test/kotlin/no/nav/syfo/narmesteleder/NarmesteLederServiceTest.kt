package no.nav.syfo.narmesteleder

import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.db.getAnsatte
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Arbeidsgiverinfo
import no.nav.syfo.narmesteleder.arbeidsforhold.service.ArbeidsgiverService
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class NarmesteLederServiceTest : Spek({

    val database = mockk<DatabaseInterface>(relaxed = true)

    val pdlPersonService = mockk<PdlPersonService>()
    val arbeidsgiverService = mockk<ArbeidsgiverService>()

    val service = NarmesteLederService(database, pdlPersonService)

    mockkStatic("no.nav.syfo.db.NarmesteLederQueriesKt")

    beforeEachTest {
        clearMocks(pdlPersonService, arbeidsgiverService)
    }

    describe("Get ansatte") {
        it("should get one ansatt") {
            runBlocking {
                coEvery { pdlPersonService.getPersoner(any(), any()) } returns mapOf(
                    "1" to PdlPerson(Navn("ansatt", null, "etternavn"), "1", "aktorId"),
                    "2" to PdlPerson(Navn("leder", null, "etternavn"), "2", "aktorIdLeder")
                )
                every { database.getAnsatte("2") } returns listOf(
                    getNarmestelederRelasjon()
                )
                coEvery { arbeidsgiverService.getArbeidsgivere("2", "token", true) } returns listOf(
                    Arbeidsgiverinfo(
                        orgnummer = "123456789",
                        juridiskOrgnummer = "123456780",
                        aktivtArbeidsforhold = true
                    )
                )

                val ansatte = service.getAnsatte("2", "callId")
                ansatte.size shouldBeEqualTo 1
            }
        }
        it("Skal ikke kalle pdl eller andre tjenester når man ikke finner narmesteleder relasjoner i databasen") {
            every { database.getAnsatte(any()) } returns emptyList()
            runBlocking {
                val ansatte = service.getAnsatte("2", "callid")
                ansatte.size shouldBeEqualTo 0
                coVerify(exactly = 0) { pdlPersonService.getPersoner(any(), any()) }
                coVerify(exactly = 0) { arbeidsgiverService.getArbeidsgivere(any(), any(), any()) }
                coVerify(exactly = 0) { pdlPersonService.getPersoner(any(), any()) }
            }
        }
    }
})

private fun getNarmestelederRelasjon() = NarmesteLederRelasjon(
    narmesteLederId = UUID.randomUUID(),
    fnr = "1",
    orgnummer = "123456789",
    narmesteLederFnr = "2",
    narmesteLederTelefonnummer = "",
    narmesteLederEpost = "",
    aktivFom = LocalDate.now(),
    aktivTom = null,
    arbeidsgiverForskutterer = true,
    skrivetilgang = false,
    tilganger = emptyList(),
    timestamp = OffsetDateTime.now(),
    null
)
