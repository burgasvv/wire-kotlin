package org.burgas.router

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.burgas.service.MessageFileService
import java.util.*

fun Application.configureMessageFileRouter() {

    val messageFileService = MessageFileService()

    routing {

        route("/api/v1/message-files") {

            get("/by-id") {
                val fileId = UUID.fromString(call.parameters["fileId"])
                val messageFileEntity = messageFileService.findEntity(fileId)
                call.respondBytes(ContentType.parse(messageFileEntity.contentType), HttpStatusCode.OK) {
                    messageFileEntity.data.bytes
                }
            }
        }
    }
}