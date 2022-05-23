package no.nav.syfo.pdl.client

import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.testutils.HttpClientTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import java.io.File

class PdlClientTest : FunSpec({

    val httpClient = HttpClientTest()

    val graphQlQuery = File("src/main/resources/graphql/getPerson.graphql").readText().replace(Regex("[\n\t]"), "")
    val pdlClient = PdlClient(httpClient.httpClient, "graphqlend", graphQlQuery)

    context("getPerson OK") {
        test("Skal få hente person fra pdl") {
            httpClient.respond(getTestData())

            val response = pdlClient.getPersoner(listOf("12345678910", "01987654321"), "Bearer token")

            response.data.hentPersonBolk shouldNotBeEqualTo null
            response.data.hentPersonBolk?.size shouldBeEqualTo 2
            response.data.hentIdenterBolk shouldNotBeEqualTo null
            response.data.hentIdenterBolk?.size shouldBeEqualTo 2
            val personBolk = response.data.hentPersonBolk?.find { it.ident == "12345678910" }
            personBolk?.person?.navn!![0].fornavn shouldBeEqualTo "RASK"
            personBolk.person?.navn!![0].etternavn shouldBeEqualTo "SAKS"
            val identBolk = response.data.hentIdenterBolk?.find { it.ident == "12345678910" }
            identBolk?.identer?.size shouldBeEqualTo 2
            identBolk?.identer?.find { it.gruppe == "AKTORID" }?.ident shouldBeEqualTo "99999999999"
            val personBolk2 = response.data.hentPersonBolk?.find { it.ident == "01987654321" }
            personBolk2?.person?.navn!![0].fornavn shouldBeEqualTo "GLAD"
            personBolk2.person?.navn!![0].etternavn shouldBeEqualTo "BOLLE"
            val identBolk2 = response.data.hentIdenterBolk?.find { it.ident == "01987654321" }
            identBolk2?.identer?.size shouldBeEqualTo 2
            identBolk2?.identer?.find { it.gruppe == "AKTORID" }?.ident shouldBeEqualTo "88888888888"
        }
        test("Skal få hentPerson = null og hentIdent = null ved error") {
            httpClient.respond(getErrorResponse())

            val response = pdlClient.getPersoner(listOf("12345678910", "01987654321"), "Bearer token")

            response.data.hentPersonBolk shouldBeEqualTo null
            response.data.hentIdenterBolk shouldBeEqualTo null
            response.errors?.size shouldBeEqualTo 1
            response.errors!![0].message shouldBeEqualTo "Ikke tilgang til å se person"
        }
    }
})
