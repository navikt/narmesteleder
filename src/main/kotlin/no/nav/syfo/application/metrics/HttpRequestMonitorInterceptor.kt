package no.nav.syfo.application.metrics

import io.ktor.server.application.*
import io.ktor.server.request.path
import io.ktor.util.pipeline.*

val REGEX = """[0-9]{9}""".toRegex()
val UUID_REGEX =
    """[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""".toRegex()

fun monitorHttpRequests(): PipelineInterceptor<Unit, PipelineCall> {
    return {
        val path = context.request.path()
        val label = getLabel(path)
        val timer = HTTP_HISTOGRAM.labels(label).startTimer()
        proceed()
        timer.observeDuration()
    }
}

fun getLabel(path: String): String {
    var label = UUID_REGEX.replace(path, ":id")
    label = REGEX.replace(label, ":orgnummer")
    return label
}
