package no.nav.syfo.narmesteleder.arbeidsforhold.model

data class Arbeidsforhold(
    val arbeidsgiver: Arbeidsgiver,
    val opplysningspliktig: Opplysningspliktig,
    val ansettelsesperiode: Ansettelsesperiode
)
