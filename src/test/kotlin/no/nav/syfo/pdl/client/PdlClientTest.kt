package no.nav.syfo.pdl.client

import kotlinx.coroutines.runBlocking
import java.io.File
import no.nav.syfo.testutils.HttpClientTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.junit.jupiter.api.Test

internal class PdlClientTest {
    private val httpClient = HttpClientTest()

    private val graphQlQuery =
        File("src/main/resources/graphql/getPerson.graphql")
            .readText()
            .replace(Regex("[\n\t]"), "")
    private val pdlClient = PdlClient(httpClient.httpClient, "graphqlend", graphQlQuery)


    @Test
    internal fun `Skal faa hente person fra pdl`() {
        httpClient.respond(getTestData())

        runBlocking {
            val response =
                pdlClient.getPersoner(listOf("12345678910", "01987654321"), "Bearer token")

            response.data.hentPersonBolk shouldNotBeEqualTo null
            response.data.hentPersonBolk?.size shouldBeEqualTo 2
            response.data.hentIdenterBolk shouldNotBeEqualTo null
            response.data.hentIdenterBolk?.size shouldBeEqualTo 2
            val personBolk = response.data.hentPersonBolk?.find { it.ident == "12345678910" }
            personBolk?.person?.navn!![0].fornavn shouldBeEqualTo "RASK"
            personBolk.person?.navn!![0].etternavn shouldBeEqualTo "SAKS"
            val identBolk = response.data.hentIdenterBolk?.find { it.ident == "12345678910" }
            identBolk?.identer?.size shouldBeEqualTo 2
            identBolk?.identer?.find { it.gruppe == "AKTORID" }?.ident shouldBeEqualTo
                "99999999999"
            val personBolk2 = response.data.hentPersonBolk?.find { it.ident == "01987654321" }
            personBolk2?.person?.navn!![0].fornavn shouldBeEqualTo "GLAD"
            personBolk2.person?.navn!![0].etternavn shouldBeEqualTo "BOLLE"
            val identBolk2 = response.data.hentIdenterBolk?.find { it.ident == "01987654321" }
            identBolk2?.identer?.size shouldBeEqualTo 2
            identBolk2?.identer?.find { it.gruppe == "AKTORID" }?.ident shouldBeEqualTo
                "88888888888"
        }
    }

    @Test
    internal fun `Skal få hentPerson = null og hentIdent = null ved error`() {
        httpClient.respond(getErrorResponse())

        runBlocking {
            val response =
                pdlClient.getPersoner(listOf("12345678910", "01987654321"), "Bearer token")

            response.data.hentPersonBolk shouldBeEqualTo null
            response.data.hentIdenterBolk shouldBeEqualTo null
            response.errors?.size shouldBeEqualTo 1
            response.errors!![0].message shouldBeEqualTo "Ikke tilgang til å se person"
        }
    }
}
