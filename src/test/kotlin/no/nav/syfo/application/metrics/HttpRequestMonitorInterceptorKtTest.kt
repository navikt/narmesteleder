package no.nav.syfo.application.metrics

import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class HttpRequestMonitorInterceptorKtTest : Spek({
    describe("Regex for metrikker") {
        it("Skal matche på aktørid") {
            val path = "/narmesteleder/sykmeldt/1000095264725/narmesteledere"

            val oppdatertPath = REGEX.replace(path, ":aktorId")

            oppdatertPath shouldBeEqualTo "/narmesteleder/sykmeldt/:aktorId/narmesteledere"
        }
    }
})
