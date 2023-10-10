package no.nav.syfo.narmesteleder

import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.runBlocking
import no.nav.syfo.testutils.TestDB
import no.nav.syfo.testutils.dropData
import no.nav.syfo.testutils.lagreNarmesteleder
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@DelicateCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class UtvidetNarmesteLederServiceTest {
    val testDb = TestDB()
    val utvidetNarmesteLederService = NarmesteLederService(testDb)

    val fnr = "fnr"
    val fnrLeder1 = "123"
    val fnrLeder2 = "456"

    @AfterEach
    fun afterEach() {
        testDb.connection.dropData()
    }

    @AfterAll
    fun afterAll() {
        testDb.stop()
    }

    @Test
    internal fun `Setter riktig navn paa ledere`() {
        testDb.connection.lagreNarmesteleder(
            orgnummer = "orgnummer",
            fnr = fnr,
            fnrNl = fnrLeder1,
            arbeidsgiverForskutterer = true,
            brukerNavn = "sykmeldt",
            narmestelederNavn = "Fornavn Ekstranavn Etternavn",
        )
        testDb.connection.lagreNarmesteleder(
            orgnummer = "orgnummer2",
            fnr = fnr,
            fnrNl = fnrLeder2,
            arbeidsgiverForskutterer = true,
            aktivTom =
            OffsetDateTime.now(
                ZoneOffset.UTC,
            )
                .minusDays(2),
            brukerNavn = "sykmeldt",
            narmestelederNavn = "Fornavn2 Mellomnavn Bindestrek-Etternavn",
        )

        runBlocking {

            val narmesteLedereMedNavn =
                utvidetNarmesteLederService.hentNarmesteledereMedNavn(fnr)

            narmesteLedereMedNavn.size shouldBeEqualTo 2
            val nl1 = narmesteLedereMedNavn.find { it.narmesteLederFnr == fnrLeder1 }
            nl1?.navn shouldBeEqualTo "Fornavn Ekstranavn Etternavn"
            val nl2 = narmesteLedereMedNavn.find { it.narmesteLederFnr == fnrLeder2 }
            nl2?.navn shouldBeEqualTo "Fornavn2 Mellomnavn Bindestrek-Etternavn"
        }
    }

    @Test
    internal fun `Returnerer tom liste hvis bruker ikke har noen nærmeste ledere`() {
        runBlocking {
            val narmesteLedereMedNavn =
                utvidetNarmesteLederService.hentNarmesteledereMedNavn(fnr)

            narmesteLedereMedNavn.size shouldBeEqualTo 0
        }
    }
}
