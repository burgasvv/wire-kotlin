package org.burgas.sse

import io.ktor.server.application.*
import io.ktor.server.sse.*

fun Application.configureServerSentEvent() {
    install(SSE)
}