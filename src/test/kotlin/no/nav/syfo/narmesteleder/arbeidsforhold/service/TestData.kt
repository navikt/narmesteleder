package no.nav.syfo.narmesteleder.arbeidsforhold.service

import java.time.LocalDate
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Ansettelsesperiode
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Arbeidsforhold
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Arbeidsgiver
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Opplysningspliktig
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Periode

fun getArbeidsgiverforhold(
    ansettelsesperiode: Ansettelsesperiode =
        Ansettelsesperiode(
            Periode(fom = LocalDate.of(2020, 6, 1), tom = null),
        ),
): List<Arbeidsforhold> {
    return listOf(
        Arbeidsforhold(
            Arbeidsgiver("Organisasjon", "123456789"),
            Opplysningspliktig("Organisasjon", "987654321"),
            ansettelsesperiode,
        ),
    )
}
