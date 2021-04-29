package no.nav.syfo.narmesteleder.oppdatering

import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.db.finnAktiveNarmestelederkoblinger
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.arbeidsforhold.service.ArbeidsgiverService
import no.nav.syfo.narmesteleder.oppdatering.kafka.NLRequestProducer
import no.nav.syfo.narmesteleder.oppdatering.kafka.NLResponseProducer
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.KafkaMetadata
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.NlKafkaMetadata
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.NlRequest
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.NlRequestKafkaMessage
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.NlResponseKafkaMessage
import no.nav.syfo.narmesteleder.oppdatering.model.NlAvbrutt
import no.nav.syfo.narmesteleder.syfonarmesteleder.client.SyfonarmestelederClient
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.model.toFormattedNameString
import no.nav.syfo.pdl.service.PdlPersonService
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@KtorExperimentalAPI
class DeaktiverNarmesteLederService(
    private val nlResponseProducer: NLResponseProducer,
    private val nlRequestProducer: NLRequestProducer,
    private val arbeidsgiverService: ArbeidsgiverService,
    private val pdlPersonService: PdlPersonService,
    private val database: DatabaseInterface,
    private val syfonarmestelederClient: SyfonarmestelederClient
) {
    suspend fun deaktiverNarmesteLederForAnsatt(fnrLeder: String, orgnummer: String, fnrSykmeldt: String, token: String, callId: UUID) {
        val aktuelleNlKoblinger = database.finnAktiveNarmestelederkoblinger(fnrLeder).filter { it.orgnummer == orgnummer && it.fnr == fnrSykmeldt }
        if (aktuelleNlKoblinger.isNotEmpty()) {
            log.info("Deaktiverer NL-koblinger for $callId")
            deaktiverNarmesteLeder(orgnummer = aktuelleNlKoblinger.first().orgnummer, fnrSykmeldt = aktuelleNlKoblinger.first().fnr, token = token, callId = callId, forespurtAvAnsatt = false)
        } else {
            val personer = pdlPersonService.getPersoner(listOf(fnrLeder, fnrSykmeldt), callId.toString())
            if (kanDeaktivereNlKobling(personer = personer, fnrLeder = fnrLeder, orgnummer = orgnummer, fnrSykmeldt = fnrSykmeldt, callId = callId)) {
                log.info("Deaktiverer NL-kobling $callId")
                deaktiverNarmesteLeder(orgnummer = orgnummer, fnrSykmeldt = fnrSykmeldt, token = token, callId = callId, forespurtAvAnsatt = false)
            } else {
                log.info("Ingen aktive koblinger å deaktivere $callId")
            }
        }
    }

    suspend fun deaktiverNarmesteLeder(orgnummer: String, fnrSykmeldt: String, token: String, callId: UUID, personer: Map<String, PdlPerson?>? = null, forespurtAvAnsatt: Boolean = true) {
        val aktivtArbeidsforhold = arbeidsgiverService.getArbeidsgivere(fnr = fnrSykmeldt, token = token, forespurtAvAnsatt = forespurtAvAnsatt)
            .firstOrNull { it.orgnummer == orgnummer && it.aktivtArbeidsforhold }

        if (aktivtArbeidsforhold != null) {
            log.info("Ber om ny nærmeste leder siden arbeidsforhold er aktivt, $callId")
            val navn = if (personer == null) {
                pdlPersonService.getPersoner(fnrs = listOf(fnrSykmeldt), callId = callId.toString())[fnrSykmeldt]?.navn
            } else {
                personer[fnrSykmeldt]?.navn
            }
            nlRequestProducer.send(
                NlRequestKafkaMessage(
                    nlRequest = NlRequest(
                        requestId = callId,
                        sykmeldingId = null,
                        fnr = fnrSykmeldt,
                        orgnr = orgnummer,
                        name = navn?.toFormattedNameString() ?: throw RuntimeException("Fant ikke navn på ansatt i PDL $callId")
                    ),
                    metadata = NlKafkaMetadata(
                        timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                        source = "user"
                    )
                )
            )
        }
        nlResponseProducer.send(
            NlResponseKafkaMessage(
                kafkaMetadata = KafkaMetadata(
                    timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                    source = "user"
                ),
                nlAvbrutt = NlAvbrutt(
                    orgnummer = orgnummer,
                    sykmeldtFnr = fnrSykmeldt,
                    aktivTom = OffsetDateTime.now(ZoneOffset.UTC)
                ),
                nlResponse = null
            )
        )
    }

    // Denne kan fjernes når narmesteleder-databasen har fått inn data fra syfoservice
    private suspend fun kanDeaktivereNlKobling(personer: Map<String, PdlPerson?>, fnrLeder: String, orgnummer: String, fnrSykmeldt: String, callId: UUID): Boolean {
        val aktorIdLeder = personer[fnrLeder]?.aktorId
        val aktorIdSykmeldt = personer[fnrSykmeldt]?.aktorId
        if (aktorIdLeder == null || aktorIdSykmeldt == null) {
            log.warn("Finner ikke leder eller ansatt i PDL, $callId")
            throw RuntimeException("Finner ikke leder eller ansatt i PDL")
        }
        val nlKoblinger = syfonarmestelederClient.getAktiveNarmestelederKoblinger(aktorIdLeder, callId.toString())

        return nlKoblinger.any { it.aktivTom == null && it.orgnummer == orgnummer && it.aktorId == aktorIdSykmeldt }
    }
}
