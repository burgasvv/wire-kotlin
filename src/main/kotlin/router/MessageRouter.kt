package org.burgas.router

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import org.burgas.dao.MessageEntity
import org.burgas.database.DatabaseConnection
import org.burgas.service.MessageService
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

fun Application.configureMessageRouter() {

    val messageService = MessageService()

    routing {

        @Suppress("DEPRECATION")
        intercept(ApplicationCallPipeline.Call) {
            if (
                call.request.path().equals("/api/v1/messages/by-id", false) ||
                call.request.path().equals("/api/v1/messages/delete", false)
            ) {
                val principal = call.principal<UserPasswordCredential>()!!
                val messageId = UUID.fromString(call.parameters["messageId"])

                newSuspendedTransaction(
                    db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    val messageEntity = MessageEntity.findById(messageId)!!
                    if (messageEntity.sender!!.email == principal.name) {
                        proceed()
                    } else {
                        throw IllegalArgumentException("identity not authorized")
                    }
                }

            } else {
                proceed()
            }
        }

        route("/api/v1/messages") {

            authenticate("basic-auth-all") {

                get("/by-id") {
                    val messageId = UUID.fromString(call.parameters["messageId"])
                    call.respond(HttpStatusCode.OK, messageService.findById(messageId))
                }

                post("/create") {
                    messageService.create(call.receiveMultipart())
                    call.respond(HttpStatusCode.OK)
                }

                delete("/delete") {
                    val messageId = UUID.fromString(call.parameters["messageId"])
                    messageService.delete(messageId)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}