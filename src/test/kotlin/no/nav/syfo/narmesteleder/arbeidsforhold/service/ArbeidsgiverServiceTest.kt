package no.nav.syfo.narmesteleder.arbeidsforhold.service

import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.OidcToken
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.narmesteleder.arbeidsforhold.client.ArbeidsforholdClient
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Arbeidsavtale
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Arbeidsforhold
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Arbeidsgiver
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Gyldighetsperiode
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Opplysningspliktig
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

@KtorExperimentalAPI
class ArbeidsgiverServiceTest : Spek({
    val arbeidsforholdClient = mockk<ArbeidsforholdClient>()
    val stsOidcToken = mockk<StsOidcClient>()
    val arbeidsgiverService = ArbeidsgiverService(arbeidsforholdClient, stsOidcToken)

    coEvery { stsOidcToken.oidcToken() } returns OidcToken("token", "jwt", 1L)

    beforeEachTest {
        clearMocks(arbeidsforholdClient)
    }

    describe("Test ArbeidsgiverService") {
        it("arbeidsgiverService returnerer arbeidsforhold") {
            coEvery { arbeidsforholdClient.getArbeidsforhold(any(), any(), any(), any()) } returns getArbeidsgiverforhold(
                Gyldighetsperiode(fom = LocalDate.now(), tom = null)
            )
            runBlocking {
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", forespurtAvAnsatt = true)
                arbeidsgiverinformasjon.size shouldBeEqualTo 1
                arbeidsgiverinformasjon[0].aktivtArbeidsforhold shouldBeEqualTo true
                arbeidsgiverinformasjon[0].orgnummer shouldBeEqualTo "123456789"
                arbeidsgiverinformasjon[0].juridiskOrgnummer shouldBeEqualTo "987654321"
            }
        }
        it("arbeidsgiverService returnerer tom liste hvis bruker ikke har arbeidsforhold") {
            coEvery { arbeidsforholdClient.getArbeidsforhold(any(), any(), any(), any()) } returns emptyList()
            runBlocking {
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", forespurtAvAnsatt = true)
                arbeidsgiverinformasjon.size shouldBeEqualTo 0
            }
        }

        it("Skal ikke hente arbeidsgiver når dato er før FOM dato i arbeidsavtale") {
            coEvery { arbeidsforholdClient.getArbeidsforhold(any(), any(), any(), any()) } returns getArbeidsgiverforhold(
                Gyldighetsperiode(fom = LocalDate.now().plusDays(1), tom = LocalDate.now().plusMonths(1))
            )
            runBlocking {
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", forespurtAvAnsatt = true)
                arbeidsgiverinformasjon.size shouldBeEqualTo 0
            }
        }

        it("Skal ikke hente arbeidsgiver når dato er etter TOM dato i arbeidsavtale") {
            coEvery { arbeidsforholdClient.getArbeidsforhold(any(), any(), any(), any()) } returns getArbeidsgiverforhold(
                Gyldighetsperiode(fom = LocalDate.now().plusDays(1), tom = LocalDate.now().plusDays(2))
            )
            runBlocking {
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", forespurtAvAnsatt = true, LocalDate.now().plusDays(3))
                arbeidsgiverinformasjon.size shouldBeEqualTo 0
            }
        }

        it("Skal hente arbeidsgiver når dato er etter FOM og TOM er null i arbeidsavtale") {
            coEvery { arbeidsforholdClient.getArbeidsforhold(any(), any(), any(), any()) } returns getArbeidsgiverforhold(
                Gyldighetsperiode(fom = LocalDate.now().plusDays(1), tom = null)
            )
            runBlocking {
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", forespurtAvAnsatt = true, LocalDate.now().plusDays(3))
                arbeidsgiverinformasjon.size shouldBeEqualTo 1
            }
        }
        it("Skal ikke hente arbeidsgiver når FOM null i arbeidsavtale") {
            coEvery { arbeidsforholdClient.getArbeidsforhold(any(), any(), any(), any()) } returns getArbeidsgiverforhold(
                Gyldighetsperiode(fom = null, tom = null)
            )
            runBlocking {
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", forespurtAvAnsatt = true, LocalDate.now().plusDays(3))
                arbeidsgiverinformasjon.size shouldBeEqualTo 0
            }
        }

        it("arbeidsgiverService should filter out duplicates") {
            coEvery { arbeidsforholdClient.getArbeidsforhold(any(), any(), any(), any()) } returns listOf(
                Arbeidsforhold(
                    Arbeidsgiver("Organisasjon", "123456789"),
                    Opplysningspliktig("Organisasjon", "987654321"),
                    listOf(
                        Arbeidsavtale(
                            gyldighetsperiode = Gyldighetsperiode(
                                fom = LocalDate.now(),
                                tom = null
                            ),
                            stillingsprosent = 100.0
                        )
                    )
                ),
                Arbeidsforhold(
                    Arbeidsgiver("Organisasjon", "123456789"),
                    Opplysningspliktig("Organisasjon", "987654321"),
                    listOf(
                        Arbeidsavtale(
                            gyldighetsperiode = Gyldighetsperiode(
                                fom = LocalDate.now(),
                                tom = null
                            ),
                            stillingsprosent = 100.0
                        )
                    )
                ),
                Arbeidsforhold(
                    Arbeidsgiver("Organisasjon", "234567891"),
                    Opplysningspliktig("Organisasjon", "987654321"),
                    listOf(
                        Arbeidsavtale(
                            gyldighetsperiode = Gyldighetsperiode(
                                fom = LocalDate.now(),
                                tom = null
                            ),
                            stillingsprosent = 100.0
                        )
                    )
                )
            )
            runBlocking {
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", forespurtAvAnsatt = true)
                arbeidsgiverinformasjon.size shouldBeEqualTo 2
                arbeidsgiverinformasjon[0].aktivtArbeidsforhold shouldBeEqualTo true
            }
        }
    }
})
