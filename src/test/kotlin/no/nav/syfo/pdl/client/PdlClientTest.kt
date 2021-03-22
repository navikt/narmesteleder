package no.nav.syfo.pdl.client

import kotlinx.coroutines.runBlocking
import no.nav.syfo.testutils.HttpClientTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.File

class PdlClientTest : Spek({

    val httpClient = HttpClientTest()

    val graphQlQuery = File("src/main/resources/graphql/getPerson.graphql").readText().replace(Regex("[\n\t]"), "")
    val pdlClient = PdlClient(httpClient.httpClient, "graphqlend", graphQlQuery)

    describe("getPerson OK") {
        it("Skal få hente person fra pdl") {
            httpClient.respond(getTestData())
            runBlocking {
                val response = pdlClient.getPersoner(listOf("12345678910", "01987654321"), "Bearer token")
                response.data.hentPersonBolk shouldNotBeEqualTo null
                response.data.hentPersonBolk?.size shouldBeEqualTo 2
                val personBolk = response.data.hentPersonBolk?.find { it.ident == "12345678910" }
                personBolk?.person?.navn!![0].fornavn shouldBeEqualTo "RASK"
                personBolk.person?.navn!![0].etternavn shouldBeEqualTo "SAKS"
                val personBolk2 = response.data.hentPersonBolk?.find { it.ident == "01987654321" }
                personBolk2?.person?.navn!![0].fornavn shouldBeEqualTo "GLAD"
                personBolk2.person?.navn!![0].etternavn shouldBeEqualTo "BOLLE"
            }
        }
        it("Skal få hentPerson = null ved error") {
            httpClient.respond(getErrorResponse())
            runBlocking {
                val response = pdlClient.getPersoner(listOf("12345678910", "01987654321"), "Bearer token")
                response.data.hentPersonBolk shouldBeEqualTo null
                response.errors?.size shouldBeEqualTo 1
                response.errors!![0].message shouldBeEqualTo "Ikke tilgang til å se person"
            }
        }
    }
})
