package no.nav.syfo.narmesteleder.oppdatering

import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.syfo.narmesteleder.oppdatering.kafka.NLResponseProducer
import no.nav.syfo.testutils.TestDB
import no.nav.syfo.testutils.dropData
import no.nav.syfo.testutils.lagreNarmesteleder
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class DeaktiverNarmesteLederServiceTest : Spek({
    val nlResponseProducer = mockk<NLResponseProducer>(relaxed = true)
    val testDb = TestDB()
    val deaktiverNarmesteLederService = DeaktiverNarmesteLederService(
        nlResponseProducer,
        testDb
    )

    val sykmeldtFnr = "12345678910"
    val lederFnr = "01987654321"

    beforeEachTest {
        clearMocks(nlResponseProducer)
    }
    afterEachTest {
        testDb.connection.dropData()
    }
    afterGroup {
        testDb.stop()
    }

    describe("DeaktiverNarmesteLederService - deaktiver kobling for ansatt") {
        it("Deaktiverer kobling hvis finnes aktiv NL-kobling i databasen") {
            testDb.connection.lagreNarmesteleder(
                orgnummer = "orgnummer", fnr = sykmeldtFnr, fnrNl = lederFnr, arbeidsgiverForskutterer = true,
                aktivFom = OffsetDateTime.now(
                    ZoneOffset.UTC
                ).minusYears(1)
            )

            runBlocking {
                deaktiverNarmesteLederService.deaktiverNarmesteLederForAnsatt(
                    lederFnr,
                    "orgnummer",
                    sykmeldtFnr,
                    UUID.randomUUID()
                )
                verify { nlResponseProducer.send(match { it.kafkaMetadata.source == "leder" }) }
            }
        }
        it("Deaktiverer ikke kobling hvis NL-kobling i databasen gjelder annen ansatt") {
            testDb.connection.lagreNarmesteleder(
                orgnummer = "orgnummer", fnr = "12312345678", fnrNl = lederFnr, arbeidsgiverForskutterer = true,
                aktivFom = OffsetDateTime.now(
                    ZoneOffset.UTC
                ).minusYears(1)
            )

            runBlocking {
                deaktiverNarmesteLederService.deaktiverNarmesteLederForAnsatt(
                    lederFnr,
                    "orgnummer",
                    sykmeldtFnr,
                    UUID.randomUUID()
                )
                verify(exactly = 0) { nlResponseProducer.send(any()) }
            }
        }
    }
})
