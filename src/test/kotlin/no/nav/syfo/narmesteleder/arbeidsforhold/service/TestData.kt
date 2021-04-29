package no.nav.syfo.narmesteleder.arbeidsforhold.service

import no.nav.syfo.narmesteleder.arbeidsforhold.model.Arbeidsavtale
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Arbeidsforhold
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Arbeidsgiver
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Gyldighetsperiode
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Opplysningspliktig
import java.time.LocalDate

fun getArbeidsgiverforhold(
    gyldighetsperiode: Gyldighetsperiode = Gyldighetsperiode(
        LocalDate.now(),
        LocalDate.now()
    )
): List<Arbeidsforhold> {
    return listOf(
        Arbeidsforhold(
            Arbeidsgiver("Organisasjon", "123456789"),
            Opplysningspliktig("Organisasjon", "987654321"),
            listOf(
                Arbeidsavtale(gyldighetsperiode = gyldighetsperiode, stillingsprosent = 100.0)
            )
        )
    )
}
