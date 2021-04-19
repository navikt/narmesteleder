package no.nav.syfo.narmesteleder.oppdatering

import io.ktor.util.KtorExperimentalAPI
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
    private val pdlPersonService: PdlPersonService
) {
    suspend fun deaktiverNarmesteLeder(orgnummer: String, fnrSykmeldt: String, token: String, callId: UUID) {
        val aktivtArbeidsforhold = arbeidsgiverService.getArbeidsgivere(fnr = fnrSykmeldt, token = token)
            .firstOrNull { it.orgnummer == orgnummer && it.aktivtArbeidsforhold }

        if (aktivtArbeidsforhold != null) {
            log.info("Ber om ny nærmeste leder siden arbeidsforhold er aktivt, $callId")
            val navn = pdlPersonService.getPersonnavn(fnrs = listOf(fnrSykmeldt), callId = callId.toString())[fnrSykmeldt]
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
}
