package no.nav.syfo.narmesteleder.leder.model

import no.nav.syfo.narmesteleder.NarmesteLederRelasjon

internal fun NarmesteLederRelasjon.toAnsatt(): Ansatt {
    return Ansatt(fnr = fnr, navn = navn, narmestelederId = narmesteLederId, orgnummer = orgnummer)
}
