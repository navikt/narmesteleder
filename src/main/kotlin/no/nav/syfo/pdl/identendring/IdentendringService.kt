package no.nav.syfo.pdl.identendring

import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.measureTime
import kotlin.time.measureTimedValue
import kotlinx.coroutines.DelicateCoroutinesApi
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.application.metrics.NYTT_FNR_ANSATT_COUNTER
import no.nav.syfo.application.metrics.NYTT_FNR_LEDER_COUNTER
import no.nav.syfo.db.finnAktiveNarmesteledereForSykmeldt
import no.nav.syfo.db.finnAktiveNarmestelederkoblinger
import no.nav.syfo.db.updateNames
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.oppdatering.OppdaterNarmesteLederService
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.KafkaMetadata
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.NlResponseKafkaMessage
import no.nav.syfo.narmesteleder.oppdatering.model.Leder
import no.nav.syfo.narmesteleder.oppdatering.model.NlAvbrutt
import no.nav.syfo.narmesteleder.oppdatering.model.NlResponse
import no.nav.syfo.narmesteleder.oppdatering.model.Sykmeldt
import no.nav.syfo.pdl.identendring.model.Ident
import no.nav.syfo.pdl.identendring.model.IdentType
import no.nav.syfo.pdl.service.PdlPersonService

@DelicateCoroutinesApi
class IdentendringService(
    private val database: DatabaseInterface,
    private val oppdaterNarmesteLederService: OppdaterNarmesteLederService,
    private val pdlService: PdlPersonService,
) {
    suspend fun oppdaterIdent(identListe: List<Ident>) {
        if (harEndretFnr(identListe)) {
            val nyttFnr =
                identListe
                    .find { it.type == IdentType.FOLKEREGISTERIDENT && it.gjeldende }
                    ?.idnummer
                    ?: throw IllegalStateException("Mangler gyldig fnr!")
            val tidligereFnr =
                identListe.filter { it.type == IdentType.FOLKEREGISTERIDENT && !it.gjeldende }
            val erLederForNlKoblinger =
                tidligereFnr.flatMap { database.finnAktiveNarmestelederkoblinger(it.idnummer) }
            val erAnsattForNlKoblinger =
                tidligereFnr.flatMap { database.finnAktiveNarmesteledereForSykmeldt(it.idnummer) }

            if (erLederForNlKoblinger.isNotEmpty() || erAnsattForNlKoblinger.isNotEmpty()) {
                pdlService.erIdentAktiv(nyttFnr)
            }

            erLederForNlKoblinger.forEach {
                oppdaterNarmesteLederService.handterMottattNarmesteLederOppdatering(
                    nlResponseKafkaMessage =
                        NlResponseKafkaMessage(
                            kafkaMetadata =
                                KafkaMetadata(OffsetDateTime.now(ZoneOffset.UTC), "PDL"),
                            nlResponse =
                                NlResponse(
                                    orgnummer = it.orgnummer,
                                    utbetalesLonn = it.arbeidsgiverForskutterer,
                                    leder =
                                        Leder(
                                            fnr = nyttFnr,
                                            mobil = it.narmesteLederTelefonnummer,
                                            epost = it.narmesteLederEpost,
                                            fornavn = null,
                                            etternavn = null,
                                        ),
                                    sykmeldt =
                                        Sykmeldt(
                                            fnr = it.fnr,
                                            navn = null,
                                        ),
                                    aktivFom = it.aktivFom.atStartOfDay().atOffset(ZoneOffset.UTC),
                                    aktivTom = null,
                                ),
                            nlAvbrutt = null,
                        ),
                    partition = 0,
                    offset = 0,
                )
            }
            log.info(
                "Har oppdatert ${erLederForNlKoblinger.size} NL-koblinger der endret fnr er leder"
            )

            erAnsattForNlKoblinger.forEach {
                oppdaterNarmesteLederService.handterMottattNarmesteLederOppdatering(
                    nlResponseKafkaMessage =
                        NlResponseKafkaMessage(
                            kafkaMetadata =
                                KafkaMetadata(OffsetDateTime.now(ZoneOffset.UTC), "PDL"),
                            nlResponse = null,
                            nlAvbrutt =
                                NlAvbrutt(
                                    orgnummer = it.orgnummer,
                                    sykmeldtFnr = it.fnr,
                                    aktivTom = OffsetDateTime.now(ZoneOffset.UTC),
                                ),
                        ),
                    partition = 0,
                    offset = 0,
                )
                oppdaterNarmesteLederService.handterMottattNarmesteLederOppdatering(
                    nlResponseKafkaMessage =
                        NlResponseKafkaMessage(
                            kafkaMetadata =
                                KafkaMetadata(OffsetDateTime.now(ZoneOffset.UTC), "PDL"),
                            nlResponse =
                                NlResponse(
                                    orgnummer = it.orgnummer,
                                    utbetalesLonn = it.arbeidsgiverForskutterer,
                                    leder =
                                        Leder(
                                            fnr = it.narmesteLederFnr,
                                            mobil = it.narmesteLederTelefonnummer,
                                            epost = it.narmesteLederEpost,
                                            fornavn = null,
                                            etternavn = null,
                                        ),
                                    sykmeldt =
                                        Sykmeldt(
                                            fnr = nyttFnr,
                                            navn = null,
                                        ),
                                    aktivFom = it.aktivFom.atStartOfDay().atOffset(ZoneOffset.UTC),
                                    aktivTom = null,
                                ),
                            nlAvbrutt = null,
                        ),
                    partition = 0,
                    offset = 0,
                )
            }
            log.info(
                "Har oppdatert ${erAnsattForNlKoblinger.size} NL-koblinger der endret fnr er ansatt"
            )

            if (erLederForNlKoblinger.isNotEmpty()) {
                NYTT_FNR_LEDER_COUNTER.inc()
            }
            if (erAnsattForNlKoblinger.isNotEmpty()) {
                NYTT_FNR_ANSATT_COUNTER.inc()
            }
        }
    }

    suspend fun updateNames(identer: List<String>) {
        val (persons, duration) =
            measureTimedValue { pdlService.getPersonerByIdenter(identer).filterNotNull() }
        val timeInSeconds = duration.inWholeSeconds
        val leftoverMilliseconds = duration.inWholeMilliseconds % 1000

        log.info(
            "Got persons from pdl ${persons.size} in $timeInSeconds.$leftoverMilliseconds seconds"
        )

        val databaseUpdateTime = measureTime { database.updateNames(persons) }
        val timeInSecondsDatabase = databaseUpdateTime.inWholeSeconds
        val leftoverMillisecondsDatabase = databaseUpdateTime.inWholeMilliseconds % 1000

        log.info(
            "Updating Database ${persons.size} in $timeInSecondsDatabase.$leftoverMillisecondsDatabase seconds"
        )
    }

    private fun harEndretFnr(identListe: List<Ident>): Boolean {
        if (identListe.filter { it.type == IdentType.FOLKEREGISTERIDENT }.size < 2) {
            log.debug("Identendring inneholder ingen endring i fnr")
            return false
        }
        return true
    }
}
