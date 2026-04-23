package org.burgas.router

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.burgas.dto.CsrfToken
import java.util.*

fun Application.configureSecurityRouter() {
    routing {

        route("/api/v1/security") {

            get("/csrf-token") {
                val csrfToken = call.sessions.get(CsrfToken::class)
                if (csrfToken != null) {
                    call.respond(HttpStatusCode.OK, csrfToken)
                } else {
                    val newCsrfToken = CsrfToken(value = UUID.randomUUID())
                    call.sessions.set(newCsrfToken, CsrfToken::class)
                    call.respond(HttpStatusCode.OK, newCsrfToken)
                }
            }
        }
    }
}