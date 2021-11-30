package no.nav.syfo.application.metrics

import io.prometheus.client.Counter
import io.prometheus.client.Histogram

const val METRICS_NS = "narmesteleder"

val HTTP_HISTOGRAM: Histogram = Histogram.Builder()
    .labelNames("path")
    .name("requests_duration_seconds")
    .help("http requests durations for incoming requests in seconds")
    .register()

val DEAKTIVERT_AV_ANSATT_COUNTER: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("deaktivert_ansatt_counter")
    .help("Antall NL-koblinger deaktivert av den ansatte via api")
    .register()

val DEAKTIVERT_AV_LEDER_COUNTER: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("deaktivert_leder_counter")
    .help("Antall NL-koblinger deaktivert av leder via api")
    .register()

val NYTT_FNR_LEDER_COUNTER: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("fnr_leder_counter")
    .help("Antall ledere som har byttet fnr")
    .register()

val NYTT_FNR_ANSATT_COUNTER: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("fnr_ansatt_counter")
    .help("Antall ansatte som har byttet fnr")
    .register()
