package no.nav.syfo.narmesteleder.user

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.oppdatering.kafka.NLResponseProducer
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.KafkaMetadata
import no.nav.syfo.narmesteleder.oppdatering.kafka.model.NlResponseKafkaMessage
import no.nav.syfo.narmesteleder.oppdatering.model.NlAvbrutt
import java.time.OffsetDateTime
import java.time.ZoneOffset

@KtorExperimentalAPI
fun Route.registrerNarmesteLederUserApi(
    nlResponseProducer: NLResponseProducer
) {
    post("/{orgnummer}/avkreft") {
        val principal: JWTPrincipal = call.authentication.principal()!!
        val fnr = principal.payload.subject
        val orgnummer = call.parameters["orgnummer"]?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("orgnummer mangler")

        nlResponseProducer.send(
            NlResponseKafkaMessage(
                kafkaMetadata = KafkaMetadata(
                    timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                    source = "user"
                ),
                nlAvbrutt = NlAvbrutt(
                    orgnummer = orgnummer,
                    sykmeldtFnr = fnr,
                    aktivTom = OffsetDateTime.now(ZoneOffset.UTC)
                ),
                nlResponse = null
            )
        )
        log.info("Den ansatte har deaktivert NL-kobling for orgnummer $orgnummer")

        call.respond(HttpStatusCode.OK)
    }
}
