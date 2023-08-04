package no.nav.syfo.narmesteleder.oppdatering

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.DelicateCoroutinesApi
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.db.deaktiverNarmesteLeder
import no.nav.syfo.db.finnAlleNarmesteledereForSykmeldt
import no.nav.syfo.db.lagreNarmesteLeder
import no.nav.syfo.db.oppdaterNarmesteLeder
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.NarmesteLederRelasjon
import no.nav.syfo.narmesteleder.arbeidsforhold.service.ArbeidsgiverService
import no.nav.syfo.narmesteleder.oppdatering.kafka.NLRequestProducer
import no.nav.syfo.narmesteleder.oppdatering.kafka.NarmesteLederLeesahProducer
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.DEAKTIVERT_ARBEIDSFORHOLD
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.DEAKTIVERT_ARBEIDSTAKER
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.DEAKTIVERT_ARBEIDSTAKER_INNSENDT_SYKMELDING
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.DEAKTIVERT_LEDER
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.DEAKTIVERT_NY_LEDER
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.IDENTENDRING
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.NY_LEDER
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.NarmesteLederLeesah
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.NlKafkaMetadata
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.NlRequest
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.NlRequestKafkaMessage
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.NlResponseKafkaMessage
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.model.toFormattedNameString
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.securelog

@DelicateCoroutinesApi
class OppdaterNarmesteLederService(
    private val pdlPersonService: PdlPersonService,
    private val arbeidsgiverService: ArbeidsgiverService,
    private val database: DatabaseInterface,
    private val narmesteLederLeesahProducer: NarmesteLederLeesahProducer,
    private val nlRequestProducer: NLRequestProducer,
) {

    suspend fun handterMottattNarmesteLederOppdatering(
        nlResponseKafkaMessage: NlResponseKafkaMessage,
        partition: Int,
        offset: Long,
    ) {
        val callId = UUID.randomUUID().toString()
        when {
            nlResponseKafkaMessage.nlResponse != null -> {
                val sykmeldtFnr = nlResponseKafkaMessage.nlResponse.sykmeldt.fnr
                val nlFnr = nlResponseKafkaMessage.nlResponse.leder.fnr
                val orgnummer = nlResponseKafkaMessage.nlResponse.orgnummer
                val personMap = pdlPersonService.getPersoner(listOf(sykmeldtFnr, nlFnr), callId)
                val sykmeldt = personMap[sykmeldtFnr]
                val nl = personMap[nlFnr]
                requireNotNull(sykmeldt) {
                    securelog.info(
                        "Mottatt NL-skjema for ansatt som ikke finnes i PDL callId $callId, " +
                            "sykmeldtFnr: $sykmeldtFnr nlFnr: $nlFnr orgnummer: $orgnummer",
                    )
                    "Mottatt NL-skjema for ansatt som ikke finnes i PDL callId $callId partition: $partition, offset: $offset"
                }
                requireNotNull(nl) {
                    securelog.info(
                        "Mottatt NL-skjema for leder som ikke finnes i PDL callId $callId, " +
                            "nlFnr: $nlFnr orgnummer: $orgnummer",
                    )
                    "Mottatt NL-skjema for leder som ikke finnes i PDL callId $callId partition: $partition, offset: $offset"
                }

                val narmesteLedere =
                    database.finnAlleNarmesteledereForSykmeldt(
                        fnr = sykmeldtFnr,
                        orgnummer = orgnummer
                    )
                createOrUpdateNL(
                    ledere = narmesteLedere,
                    nlResponseKafkaMessage = nlResponseKafkaMessage,
                    callId = callId,
                    sykmeldt = sykmeldt,
                    leder = nl
                )
            }
            nlResponseKafkaMessage.nlAvbrutt != null -> {
                val narmesteLedere =
                    database.finnAlleNarmesteledereForSykmeldt(
                        fnr = nlResponseKafkaMessage.nlAvbrutt.sykmeldtFnr,
                        orgnummer = nlResponseKafkaMessage.nlAvbrutt.orgnummer
                    )
                deaktiverTidligereLedereVedAvbryting(
                    narmesteLedere,
                    nlResponseKafkaMessage.nlAvbrutt.aktivTom,
                    callId,
                    nlResponseKafkaMessage.kafkaMetadata.source
                )
            }
            else -> {
                log.error("Har mottatt nl-response som ikke er ny eller avbrutt")
                throw IllegalStateException("Har mottatt nl-response som ikke er ny eller avbrutt")
            }
        }
    }

    private fun createOrUpdateNL(
        ledere: List<NarmesteLederRelasjon>,
        nlResponseKafkaMessage: NlResponseKafkaMessage,
        callId: String,
        sykmeldt: PdlPerson,
        leder: PdlPerson,
    ) {
        when (
            val eksisterendeLeder =
                getExistingLeder(ledere, nlResponseKafkaMessage.nlResponse!!.leder.fnr)
        ) {
            null -> {
                deaktiverTidligereLedere(
                    ledere,
                    OffsetDateTime.now(ZoneOffset.UTC),
                    callId,
                    nlResponseKafkaMessage.kafkaMetadata.source
                )
                val narmesteLederId = UUID.randomUUID()
                database.lagreNarmesteLeder(
                    narmesteLederId,
                    nlResponseKafkaMessage.nlResponse,
                    nlResponseKafkaMessage.kafkaMetadata.timestamp,
                    sykmeldt,
                    leder
                )
                narmesteLederLeesahProducer.send(
                    NarmesteLederLeesah(
                        narmesteLederId = narmesteLederId,
                        fnr = nlResponseKafkaMessage.nlResponse.sykmeldt.fnr,
                        orgnummer = nlResponseKafkaMessage.nlResponse.orgnummer,
                        narmesteLederFnr = nlResponseKafkaMessage.nlResponse.leder.fnr,
                        narmesteLederTelefonnummer = nlResponseKafkaMessage.nlResponse.leder.mobil,
                        narmesteLederEpost = nlResponseKafkaMessage.nlResponse.leder.epost,
                        aktivFom =
                            nlResponseKafkaMessage.nlResponse.aktivFom?.let {
                                nlResponseKafkaMessage.nlResponse.aktivFom.toLocalDate()
                            }
                                ?: nlResponseKafkaMessage.kafkaMetadata.timestamp.toLocalDate(),
                        aktivTom = null,
                        arbeidsgiverForskutterer = nlResponseKafkaMessage.nlResponse.utbetalesLonn,
                        timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                        status = NY_LEDER,
                    ),
                )
                securelog.info(
                    "Created new NL for bruker_fnr ${nlResponseKafkaMessage.nlResponse.sykmeldt.fnr}, " +
                        "for narmeste_leder_fnr: ${ nlResponseKafkaMessage.nlResponse.leder.fnr}, " +
                        "narmesteLederId $narmesteLederId",
                )
                log.info("Created new NL for callId $callId")
            }
            else -> {
                val ledereSomSkalDeaktiveres = ledere.filter { it != eksisterendeLeder }
                deaktiverTidligereLedere(
                    ledereSomSkalDeaktiveres,
                    OffsetDateTime.now(ZoneOffset.UTC),
                    callId,
                    nlResponseKafkaMessage.kafkaMetadata.source
                )
                database.oppdaterNarmesteLeder(
                    narmesteLederId = eksisterendeLeder.narmesteLederId,
                    nlResponse = nlResponseKafkaMessage.nlResponse,
                    sykmeldt = sykmeldt,
                    leder = leder,
                )
                narmesteLederLeesahProducer.send(
                    NarmesteLederLeesah(
                        narmesteLederId = eksisterendeLeder.narmesteLederId,
                        fnr = eksisterendeLeder.fnr,
                        orgnummer = eksisterendeLeder.orgnummer,
                        narmesteLederFnr = eksisterendeLeder.narmesteLederFnr,
                        narmesteLederTelefonnummer = nlResponseKafkaMessage.nlResponse.leder.mobil,
                        narmesteLederEpost = nlResponseKafkaMessage.nlResponse.leder.epost,
                        aktivFom = eksisterendeLeder.aktivFom,
                        aktivTom = null,
                        arbeidsgiverForskutterer = nlResponseKafkaMessage.nlResponse.utbetalesLonn,
                        timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                        status = NY_LEDER,
                    ),
                )
                log.info(
                    "Updating existing NL with id ${eksisterendeLeder.narmesteLederId}, $callId"
                )
            }
        }
    }

    private fun deaktiverTidligereLedere(
        narmesteLedere: List<NarmesteLederRelasjon>,
        aktivTom: OffsetDateTime,
        callId: String,
        source: String
    ) {
        log.info("Deaktiverer ${narmesteLedere.size} nærmeste ledere $callId")
        narmesteLedere
            .filter { it.aktivTom == null }
            .forEach {
                database.deaktiverNarmesteLeder(
                    narmesteLederId = it.narmesteLederId,
                    aktivTom = aktivTom
                )
                narmesteLederLeesahProducer.send(
                    NarmesteLederLeesah(
                        narmesteLederId = it.narmesteLederId,
                        fnr = it.fnr,
                        orgnummer = it.orgnummer,
                        narmesteLederFnr = it.narmesteLederFnr,
                        narmesteLederTelefonnummer = it.narmesteLederTelefonnummer,
                        narmesteLederEpost = it.narmesteLederEpost,
                        aktivFom = it.aktivFom,
                        aktivTom = aktivTom.toLocalDate(),
                        arbeidsgiverForskutterer = it.arbeidsgiverForskutterer,
                        timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                        status = getStatusFromSource(source),
                    ),
                )
            }
    }

    private suspend fun deaktiverTidligereLedereVedAvbryting(
        narmesteLedere: List<NarmesteLederRelasjon>,
        aktivTom: OffsetDateTime,
        callId: String,
        source: String
    ) {
        log.info(
            "Deaktiverer ${narmesteLedere.size} nærmeste ledere som følge av avbryting $callId"
        )
        narmesteLedere
            .filter { it.aktivTom == null }
            .forEach {
                val aktivtArbeidsforhold =
                    arbeidsgiverService
                        .getArbeidsgivere(fnr = it.fnr, token = null, forespurtAvAnsatt = false)
                        .firstOrNull { arbeidsgiverinfo ->
                            arbeidsgiverinfo.orgnummer == it.orgnummer &&
                                arbeidsgiverinfo.aktivtArbeidsforhold
                        }

                database.deaktiverNarmesteLeder(
                    narmesteLederId = it.narmesteLederId,
                    aktivTom = aktivTom
                )
                narmesteLederLeesahProducer.send(
                    NarmesteLederLeesah(
                        narmesteLederId = it.narmesteLederId,
                        fnr = it.fnr,
                        orgnummer = it.orgnummer,
                        narmesteLederFnr = it.narmesteLederFnr,
                        narmesteLederTelefonnummer = it.narmesteLederTelefonnummer,
                        narmesteLederEpost = it.narmesteLederEpost,
                        aktivFom = it.aktivFom,
                        aktivTom = aktivTom.toLocalDate(),
                        arbeidsgiverForskutterer = it.arbeidsgiverForskutterer,
                        timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                        status = getStatusFromSource(source),
                    ),
                )
                if (aktivtArbeidsforhold != null) {
                    log.info("Ber om ny nærmeste leder siden arbeidsforhold er aktivt, $callId")
                    val navn =
                        pdlPersonService
                            .getPersoner(fnrs = listOf(it.fnr), callId = callId)[it.fnr]
                            ?.navn

                    nlRequestProducer.send(
                        NlRequestKafkaMessage(
                            nlRequest =
                                NlRequest(
                                    requestId = UUID.fromString(callId),
                                    sykmeldingId = null,
                                    fnr = it.fnr,
                                    orgnr = it.orgnummer,
                                    name = navn?.toFormattedNameString()
                                            ?: throw RuntimeException(
                                                "Fant ikke navn på ansatt i PDL $callId"
                                            ),
                                ),
                            metadata =
                                NlKafkaMetadata(
                                    timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                                    source = source,
                                ),
                        ),
                    )
                }
            }
    }

    private fun getStatusFromSource(source: String) =
        when (source) {
            "arbeidstaker" -> DEAKTIVERT_ARBEIDSTAKER
            "leder" -> DEAKTIVERT_LEDER
            "narmesteleder-arbeidsforhold" -> DEAKTIVERT_ARBEIDSFORHOLD
            "syfosmaltinn" -> DEAKTIVERT_ARBEIDSTAKER_INNSENDT_SYKMELDING
            "syfonlaltinn" -> DEAKTIVERT_NY_LEDER
            "PDL" -> IDENTENDRING
            "user" -> null
            else -> {
                log.warn("Invalid source $source")
                null
            }
        }

    private fun getExistingLeder(
        ledere: List<NarmesteLederRelasjon>,
        nlFnr: String,
    ): NarmesteLederRelasjon? {
        return ledere.maxByOrNull { it.aktivFom }.takeIf { it?.narmesteLederFnr == nlFnr }
    }
}
