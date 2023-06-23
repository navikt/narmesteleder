package no.nav.syfo.narmesteleder.arbeidsforhold.service

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDate
import no.nav.syfo.application.client.AccessTokenClientV2
import no.nav.syfo.narmesteleder.arbeidsforhold.client.ArbeidsforholdClient
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Ansettelsesperiode
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Arbeidsforhold
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Arbeidsgiver
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Opplysningspliktig
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Periode
import org.amshove.kluent.shouldBeEqualTo

class ArbeidsgiverServiceTest :
    FunSpec({
        val arbeidsforholdClient = mockk<ArbeidsforholdClient>()
        val accessTokenClientV2 = mockk<AccessTokenClientV2>()
        val arbeidsgiverService =
            ArbeidsgiverService(arbeidsforholdClient, accessTokenClientV2, "scope")

        coEvery { accessTokenClientV2.getAccessTokenV2(any()) } returns "token"

        beforeTest { clearMocks(arbeidsforholdClient) }

        context("Test ArbeidsgiverService") {
            test("arbeidsgiverService returnerer arbeidsforhold") {
                coEvery { arbeidsforholdClient.getArbeidsforhold(any(), any(), any()) } returns
                    getArbeidsgiverforhold()

                val arbeidsgiverinformasjon =
                    arbeidsgiverService.getArbeidsgivere(
                        "12345678901",
                        "token",
                        forespurtAvAnsatt = true
                    )
                arbeidsgiverinformasjon.size shouldBeEqualTo 1
                arbeidsgiverinformasjon[0].aktivtArbeidsforhold shouldBeEqualTo true
                arbeidsgiverinformasjon[0].orgnummer shouldBeEqualTo "123456789"
                arbeidsgiverinformasjon[0].juridiskOrgnummer shouldBeEqualTo "987654321"
            }
            test("arbeidsgiverService returnerer tom liste hvis bruker ikke har arbeidsforhold") {
                coEvery { arbeidsforholdClient.getArbeidsforhold(any(), any(), any()) } returns
                    emptyList()

                val arbeidsgiverinformasjon =
                    arbeidsgiverService.getArbeidsgivere(
                        "12345678901",
                        "token",
                        forespurtAvAnsatt = true
                    )
                arbeidsgiverinformasjon.size shouldBeEqualTo 0
            }
            test(
                "Viser arbeidsforhold som ikke aktivt hvis tom for ansettelsesperiode er tidligere enn i dag"
            ) {
                coEvery { arbeidsforholdClient.getArbeidsforhold(any(), any(), any()) } returns
                    getArbeidsgiverforhold(
                        Ansettelsesperiode(
                            Periode(
                                fom = LocalDate.now().minusYears(1),
                                tom = LocalDate.now().minusDays(1)
                            )
                        ),
                    )

                val arbeidsgiverinformasjon =
                    arbeidsgiverService.getArbeidsgivere(
                        "12345678901",
                        "token",
                        forespurtAvAnsatt = true
                    )
                arbeidsgiverinformasjon.size shouldBeEqualTo 1
                arbeidsgiverinformasjon[0].aktivtArbeidsforhold shouldBeEqualTo false
            }
            test("Viser arbeidsforhold som aktivt hvis tom for ansettelsesperiode er i fremtiden") {
                coEvery { arbeidsforholdClient.getArbeidsforhold(any(), any(), any()) } returns
                    getArbeidsgiverforhold(
                        Ansettelsesperiode(
                            Periode(
                                fom = LocalDate.now().minusYears(1),
                                tom = LocalDate.now().plusDays(10)
                            )
                        ),
                    )

                val arbeidsgiverinformasjon =
                    arbeidsgiverService.getArbeidsgivere(
                        "12345678901",
                        "token",
                        forespurtAvAnsatt = true
                    )
                arbeidsgiverinformasjon.size shouldBeEqualTo 1
                arbeidsgiverinformasjon[0].aktivtArbeidsforhold shouldBeEqualTo true
            }
            test(
                "arbeidsgiverService filtrerer bort duplikate arbeidsforhold for samme orgnummer"
            ) {
                coEvery { arbeidsforholdClient.getArbeidsforhold(any(), any(), any()) } returns
                    listOf(
                        Arbeidsforhold(
                            Arbeidsgiver("Organisasjon", "123456789"),
                            Opplysningspliktig("Organisasjon", "987654321"),
                            Ansettelsesperiode(
                                Periode(fom = LocalDate.of(2020, 6, 1), tom = null),
                            ),
                        ),
                        Arbeidsforhold(
                            Arbeidsgiver("Organisasjon", "123456789"),
                            Opplysningspliktig("Organisasjon", "987654321"),
                            Ansettelsesperiode(
                                Periode(fom = LocalDate.of(2020, 6, 1), tom = null),
                            ),
                        ),
                        Arbeidsforhold(
                            Arbeidsgiver("Organisasjon", "234567891"),
                            Opplysningspliktig("Organisasjon", "987654321"),
                            Ansettelsesperiode(
                                Periode(fom = LocalDate.of(2020, 6, 1), tom = null),
                            ),
                        ),
                    )

                val arbeidsgiverinformasjon =
                    arbeidsgiverService.getArbeidsgivere(
                        "12345678901",
                        "token",
                        forespurtAvAnsatt = true
                    )
                arbeidsgiverinformasjon.size shouldBeEqualTo 2
                arbeidsgiverinformasjon[0].aktivtArbeidsforhold shouldBeEqualTo true
            }
            test(
                "arbeidsgiverService velger det aktive arbeidsforholdet ved duplikate arbeidsforhold for samme orgnummer"
            ) {
                coEvery { arbeidsforholdClient.getArbeidsforhold(any(), any(), any()) } returns
                    listOf(
                        Arbeidsforhold(
                            Arbeidsgiver("Organisasjon", "123456789"),
                            Opplysningspliktig("Organisasjon", "987654321"),
                            Ansettelsesperiode(
                                Periode(
                                    fom = LocalDate.of(2020, 5, 1),
                                    tom = LocalDate.of(2020, 6, 1)
                                ),
                            ),
                        ),
                        Arbeidsforhold(
                            Arbeidsgiver("Organisasjon", "123456789"),
                            Opplysningspliktig("Organisasjon", "987654321"),
                            Ansettelsesperiode(
                                Periode(fom = LocalDate.of(2020, 1, 1), tom = null),
                            ),
                        ),
                    )

                val arbeidsgiverinformasjon =
                    arbeidsgiverService.getArbeidsgivere(
                        "12345678901",
                        "token",
                        forespurtAvAnsatt = true
                    )
                arbeidsgiverinformasjon.size shouldBeEqualTo 1
                arbeidsgiverinformasjon[0].aktivtArbeidsforhold shouldBeEqualTo true
            }
            test(
                "arbeidsgiverService velger det aktive arbeidsforholdet ved duplikate arbeidsforhold der alle har satt tom-dato for samme orgnummer"
            ) {
                coEvery { arbeidsforholdClient.getArbeidsforhold(any(), any(), any()) } returns
                    listOf(
                        Arbeidsforhold(
                            Arbeidsgiver("Organisasjon", "123456789"),
                            Opplysningspliktig("Organisasjon", "987654321"),
                            Ansettelsesperiode(
                                Periode(
                                    fom = LocalDate.now().minusYears(1),
                                    tom = LocalDate.now().minusWeeks(40)
                                ),
                            ),
                        ),
                        Arbeidsforhold(
                            Arbeidsgiver("Organisasjon", "123456789"),
                            Opplysningspliktig("Organisasjon", "987654321"),
                            Ansettelsesperiode(
                                Periode(
                                    fom = LocalDate.now().minusWeeks(40),
                                    tom = LocalDate.now().plusDays(10)
                                ),
                            ),
                        ),
                    )

                val arbeidsgiverinformasjon =
                    arbeidsgiverService.getArbeidsgivere(
                        "12345678901",
                        "token",
                        forespurtAvAnsatt = true
                    )
                arbeidsgiverinformasjon.size shouldBeEqualTo 1
                arbeidsgiverinformasjon[0].aktivtArbeidsforhold shouldBeEqualTo true
            }
        }
    })
