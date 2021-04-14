package no.nav.syfo.application.metrics

import io.ktor.application.ApplicationCall
import io.ktor.request.path
import io.ktor.util.pipeline.PipelineContext

val REGEX = """[0-9]{9}""".toRegex()

fun monitorHttpRequests(): suspend PipelineContext<Unit, ApplicationCall>.(Unit) -> Unit {
    return {
        val path = context.request.path()
        val label = REGEX.replace(path, ":orgnummer")
        val timer = HTTP_HISTOGRAM.labels(label).startTimer()
        proceed()
        timer.observeDuration()
    }
}
