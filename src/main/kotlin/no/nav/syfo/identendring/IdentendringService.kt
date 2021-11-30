package no.nav.syfo.identendring

import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.application.metrics.NYTT_FNR_ANSATT_COUNTER
import no.nav.syfo.application.metrics.NYTT_FNR_LEDER_COUNTER
import no.nav.syfo.db.finnAktiveNarmesteledereForSykmeldt
import no.nav.syfo.db.finnAktiveNarmestelederkoblinger
import no.nav.syfo.identendring.model.Ident
import no.nav.syfo.identendring.model.IdentType
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.oppdatering.OppdaterNarmesteLederService
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.KafkaMetadata
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.NlResponseKafkaMessage
import no.nav.syfo.narmesteleder.oppdatering.model.Leder
import no.nav.syfo.narmesteleder.oppdatering.model.NlAvbrutt
import no.nav.syfo.narmesteleder.oppdatering.model.NlResponse
import no.nav.syfo.narmesteleder.oppdatering.model.Sykmeldt
import java.time.OffsetDateTime
import java.time.ZoneOffset

class IdentendringService(
    private val database: DatabaseInterface,
    private val oppdaterNarmesteLederService: OppdaterNarmesteLederService
) {
    suspend fun oppdaterIdent(identListe: List<Ident>) {
        if (harEndretFnr(identListe)) {
            val nyttFnr = identListe.find { it.type == IdentType.FOLKEREGISTERIDENT && it.gjeldende }?.idnummer
                ?: throw IllegalStateException("Mangler gyldig fnr!")
            val tidligereFnr = identListe.filter { it.type == IdentType.FOLKEREGISTERIDENT && !it.gjeldende }
            val erLederForNlKoblinger = tidligereFnr.flatMap { database.finnAktiveNarmestelederkoblinger(it.idnummer) }
            val erAnsattForNlKoblinger = tidligereFnr.flatMap { database.finnAktiveNarmesteledereForSykmeldt(it.idnummer) }

            erLederForNlKoblinger.forEach {
                oppdaterNarmesteLederService.handterMottattNarmesteLederOppdatering(
                    nlResponseKafkaMessage = NlResponseKafkaMessage(
                        kafkaMetadata = KafkaMetadata(OffsetDateTime.now(ZoneOffset.UTC), "PDL"),
                        nlResponse = NlResponse(
                            orgnummer = it.orgnummer,
                            utbetalesLonn = it.arbeidsgiverForskutterer,
                            leder = Leder(
                                fnr = nyttFnr,
                                mobil = it.narmesteLederTelefonnummer,
                                epost = it.narmesteLederEpost,
                                fornavn = null,
                                etternavn = null
                            ),
                            sykmeldt = Sykmeldt(
                                fnr = it.fnr,
                                navn = null
                            ),
                            aktivFom = it.aktivFom.atStartOfDay().atOffset(ZoneOffset.UTC),
                            aktivTom = null
                        ),
                        nlAvbrutt = null
                    ),
                    partition = 0,
                    offset = 0
                )
            }
            log.info("Har oppdatert ${erLederForNlKoblinger.size} NL-koblinger der endret fnr er leder")

            erAnsattForNlKoblinger.forEach {
                oppdaterNarmesteLederService.handterMottattNarmesteLederOppdatering(
                    nlResponseKafkaMessage = NlResponseKafkaMessage(
                        kafkaMetadata = KafkaMetadata(OffsetDateTime.now(ZoneOffset.UTC), "PDL"),
                        nlResponse = null,
                        nlAvbrutt = NlAvbrutt(
                            orgnummer = it.orgnummer,
                            sykmeldtFnr = it.fnr,
                            aktivTom = OffsetDateTime.now(ZoneOffset.UTC)
                        )
                    ),
                    partition = 0,
                    offset = 0
                )
                oppdaterNarmesteLederService.handterMottattNarmesteLederOppdatering(
                    nlResponseKafkaMessage = NlResponseKafkaMessage(
                        kafkaMetadata = KafkaMetadata(OffsetDateTime.now(ZoneOffset.UTC), "PDL"),
                        nlResponse = NlResponse(
                            orgnummer = it.orgnummer,
                            utbetalesLonn = it.arbeidsgiverForskutterer,
                            leder = Leder(
                                fnr = it.narmesteLederFnr,
                                mobil = it.narmesteLederTelefonnummer,
                                epost = it.narmesteLederEpost,
                                fornavn = null,
                                etternavn = null
                            ),
                            sykmeldt = Sykmeldt(
                                fnr = nyttFnr,
                                navn = null
                            ),
                            aktivFom = it.aktivFom.atStartOfDay().atOffset(ZoneOffset.UTC),
                            aktivTom = null
                        ),
                        nlAvbrutt = null
                    ),
                    partition = 0,
                    offset = 0
                )
            }
            log.info("Har oppdatert ${erAnsattForNlKoblinger.size} NL-koblinger der endret fnr er ansatt")

            if (erLederForNlKoblinger.isNotEmpty()) {
                NYTT_FNR_LEDER_COUNTER.inc()
            }
            if (erAnsattForNlKoblinger.isNotEmpty()) {
                NYTT_FNR_ANSATT_COUNTER.inc()
            }
        }
    }

    private fun harEndretFnr(identListe: List<Ident>): Boolean {
        if (identListe.filter { it.type == IdentType.FOLKEREGISTERIDENT }.size < 2) {
            log.debug("Identendring inneholder ingen endring i fnr")
            return false
        }
        return true
    }
}
