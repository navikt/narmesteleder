package no.nav.syfo.orgnummer.db

import no.nav.syfo.orgnummer.kafka.ArbeidsgiverStatus
import no.nav.syfo.testutils.TestDB
import no.nav.syfo.testutils.dropData
import org.amshove.kluent.`should contain same`
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContainAll
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class OrgnummerDBKtTest : Spek({
    val database = TestDB()

    beforeEachTest {
        database.connection.dropData()
    }
    describe("Test orgnummer db") {
        it("Should get null when orgnummer is not saved") {
            val juridisk = database.getJuridiskOrgnummer("1")
            juridisk shouldBe null
        }
        it("should save orgnummer") {
            database.saveOrUpdateOrgnummer("1", "juridisk")
            val juridisk = database.getJuridiskOrgnummer("1")
            juridisk shouldBeEqualTo "juridisk"
        }
        it("Should update juridisk orgnummer") {
            database.saveOrUpdateOrgnummer("1", "1")
            database.getJuridiskOrgnummer("1") shouldBeEqualTo "1"
            database.saveOrUpdateOrgnummer("1", "2")
            database.getJuridiskOrgnummer("1") shouldBeEqualTo "2"
        }
        it("Should get orgnummer for juridisk") {
            database.saveOrUpdateOrgnummer("1", "1")
            database.saveOrUpdateOrgnummer("2", "1")
            database.saveOrUpdateOrgnummer("3", "1")
            database.saveOrUpdateOrgnummer("4", "2")
            val orgnummer = database.getOrgnummer("1")
            orgnummer.size shouldBeEqualTo 3
            orgnummer shouldContainAll listOf("1", "2", "3")
        }
        it("Insert batch") {
            val toUpdate = listOf<ArbeidsgiverStatus>(
                ArbeidsgiverStatus("1", "11"),
                ArbeidsgiverStatus("2", "11"),
                ArbeidsgiverStatus("2", "11"),
                ArbeidsgiverStatus("3", "33"),
                ArbeidsgiverStatus("4", "44"),
                ArbeidsgiverStatus("5", "44"),
                ArbeidsgiverStatus("6", "66"),
                ArbeidsgiverStatus("7", "77"),
                ArbeidsgiverStatus("1", "10"),
            ).distinct()


            database.saveOrUpdateOrgnummer(toUpdate)
            database.getJuridiskOrgnummer("1") shouldBeEqualTo "10"
            database.getJuridiskOrgnummer("2") shouldBeEqualTo "11"
            database.getJuridiskOrgnummer("3") shouldBeEqualTo "33"
            database.getJuridiskOrgnummer("4") shouldBeEqualTo "44"
            database.getJuridiskOrgnummer("5") shouldBeEqualTo "44"
            database.getJuridiskOrgnummer("6") shouldBeEqualTo "66"
            database.getJuridiskOrgnummer("7") shouldBeEqualTo "77"

            database.getOrgnummer("11") `should contain same` listOf("2")
            database.getOrgnummer("10") `should contain same` listOf("1")
            database.getOrgnummer("33") `should contain same` listOf("3")
            database.getOrgnummer("44") `should contain same` listOf("4", "5")
            database.getOrgnummer("66") `should contain same` listOf("6")
            database.getOrgnummer("77") `should contain same` listOf("7")
        }
    }
})
