package no.nav.syfo.narmesteleder

import io.kotest.core.spec.style.FunSpec
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.DelicateCoroutinesApi
import no.nav.syfo.testutils.TestDB
import no.nav.syfo.testutils.dropData
import no.nav.syfo.testutils.lagreNarmesteleder
import org.amshove.kluent.shouldBeEqualTo

@DelicateCoroutinesApi
class UtvidetNarmesteLederServiceTest :
    FunSpec({
        val testDb = TestDB()
        val utvidetNarmesteLederService = NarmesteLederService(testDb)

        val fnr = "fnr"
        val fnrLeder1 = "123"
        val fnrLeder2 = "456"

        afterTest { testDb.connection.dropData() }

        afterSpec { testDb.stop() }

        context("UtvidetNarmesteLederService") {
            test("Setter riktig navn på ledere") {
                testDb.connection.lagreNarmesteleder(
                    orgnummer = "orgnummer",
                    fnr = fnr,
                    fnrNl = fnrLeder1,
                    arbeidsgiverForskutterer = true,
                    brukerNavn = "sykmeldt",
                    narmestelederNavn = "Fornavn Ekstranavn Etternavn"
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
                    narmestelederNavn = "Fornavn2 Mellomnavn Bindestrek-Etternavn"
                )

                val narmesteLedereMedNavn =
                    utvidetNarmesteLederService.hentNarmesteledereMedNavn(fnr)

                narmesteLedereMedNavn.size shouldBeEqualTo 2
                val nl1 = narmesteLedereMedNavn.find { it.narmesteLederFnr == fnrLeder1 }
                nl1?.navn shouldBeEqualTo "Fornavn Ekstranavn Etternavn"
                val nl2 = narmesteLedereMedNavn.find { it.narmesteLederFnr == fnrLeder2 }
                nl2?.navn shouldBeEqualTo "Fornavn2 Mellomnavn Bindestrek-Etternavn"
            }
            test("Returnerer tom liste hvis bruker ikke har noen nærmeste ledere") {
                val narmesteLedereMedNavn =
                    utvidetNarmesteLederService.hentNarmesteledereMedNavn(fnr)

                narmesteLedereMedNavn.size shouldBeEqualTo 0
            }
        }
    })
