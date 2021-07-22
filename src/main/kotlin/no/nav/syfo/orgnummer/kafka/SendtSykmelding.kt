package no.nav.syfo.orgnummer.kafka

data class ArbeidsgiverStatus(
    val orgnummer: String,
    val juridiskOrgnummer: String?
)

data class SendtEvent(
    val arbeidsgiver: ArbeidsgiverStatus
)

data class SendtSykmelding(
    val event: SendtEvent
)
