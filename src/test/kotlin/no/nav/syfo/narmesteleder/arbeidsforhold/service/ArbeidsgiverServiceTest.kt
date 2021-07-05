package no.nav.syfo.narmesteleder.arbeidsforhold.service

import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.OidcToken
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.narmesteleder.arbeidsforhold.client.ArbeidsforholdClient
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Ansettelsesperiode
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Arbeidsforhold
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Arbeidsgiver
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Opplysningspliktig
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Periode
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
            coEvery { arbeidsforholdClient.getArbeidsforhold(any(), any(), any(), any()) } returns getArbeidsgiverforhold()
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
        it("Viser arbeidsforhold som ikke aktivt hvis tom er satt for ansettelsesperiode") {
            coEvery {
                arbeidsforholdClient.getArbeidsforhold(any(), any(), any(), any())
            } returns getArbeidsgiverforhold(
                Ansettelsesperiode(Periode(fom = LocalDate.now().minusYears(1), tom = LocalDate.now()))
            )
            runBlocking {
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", forespurtAvAnsatt = true)
                arbeidsgiverinformasjon.size shouldBeEqualTo 1
                arbeidsgiverinformasjon[0].aktivtArbeidsforhold shouldBeEqualTo false
            }
        }
        it("arbeidsgiverService filtrerer bort duplikate arbeidsforhold for samme orgnummer") {
            coEvery { arbeidsforholdClient.getArbeidsforhold(any(), any(), any(), any()) } returns listOf(
                Arbeidsforhold(
                    Arbeidsgiver("Organisasjon", "123456789"),
                    Opplysningspliktig("Organisasjon", "987654321"),
                    Ansettelsesperiode(
                        Periode(fom = LocalDate.of(2020, 6, 1), tom = null)
                    )
                ),
                Arbeidsforhold(
                    Arbeidsgiver("Organisasjon", "123456789"),
                    Opplysningspliktig("Organisasjon", "987654321"),
                    Ansettelsesperiode(
                        Periode(fom = LocalDate.of(2020, 6, 1), tom = null)
                    )
                ),
                Arbeidsforhold(
                    Arbeidsgiver("Organisasjon", "234567891"),
                    Opplysningspliktig("Organisasjon", "987654321"),
                    Ansettelsesperiode(
                        Periode(fom = LocalDate.of(2020, 6, 1), tom = null)
                    )
                )
            )
            runBlocking {
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", forespurtAvAnsatt = true)
                arbeidsgiverinformasjon.size shouldBeEqualTo 2
                arbeidsgiverinformasjon[0].aktivtArbeidsforhold shouldBeEqualTo true
            }
        }
        it("arbeidsgiverService velger det aktive arbeidsforholdet ved duplikate arbeidsforhold for samme orgnummer") {
            coEvery { arbeidsforholdClient.getArbeidsforhold(any(), any(), any(), any()) } returns listOf(
                Arbeidsforhold(
                    Arbeidsgiver("Organisasjon", "123456789"),
                    Opplysningspliktig("Organisasjon", "987654321"),
                    Ansettelsesperiode(
                        Periode(fom = LocalDate.of(2020, 5, 1), tom = LocalDate.of(2020, 6, 1))
                    )
                ),
                Arbeidsforhold(
                    Arbeidsgiver("Organisasjon", "123456789"),
                    Opplysningspliktig("Organisasjon", "987654321"),
                    Ansettelsesperiode(
                        Periode(fom = LocalDate.of(2020, 1, 1), tom = null)
                    )
                )
            )
            runBlocking {
                val arbeidsgiverinformasjon = arbeidsgiverService.getArbeidsgivere("12345678901", "token", forespurtAvAnsatt = true)
                arbeidsgiverinformasjon.size shouldBeEqualTo 1
                arbeidsgiverinformasjon[0].aktivtArbeidsforhold shouldBeEqualTo true
            }
        }
    }
})
