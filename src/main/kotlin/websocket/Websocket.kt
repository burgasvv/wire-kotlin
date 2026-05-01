package org.burgas.websocket

import io.ktor.server.application.*
import io.ktor.server.websocket.*

fun Application.configureWebSocket() {
    install(WebSockets) {
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
}