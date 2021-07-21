package no.nav.syfo.orgnummer.db

import no.nav.syfo.testutils.TestDB
import no.nav.syfo.testutils.dropData
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
    }
})
