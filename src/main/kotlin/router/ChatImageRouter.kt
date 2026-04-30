package org.burgas.router

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import org.burgas.dao.ChatEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.DocumentRequest
import org.burgas.dto.ImageRequest
import org.burgas.service.ChatImageService
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

fun Application.configureChatImageRouter() {

    val chatImageService = ChatImageService()

    routing {

        @Suppress("DEPRECATION")
        intercept(ApplicationCallPipeline.Call) {
            if (
                call.request.path().equals("/api/v1/chat-images/create", false) ||
                call.request.path().equals("/api/v1/chat-images/delete", false)
            ) {
                val principal = call.principal<UserPasswordCredential>()!!
                val chatId = UUID.fromString(call.parameters["chatId"])

                newSuspendedTransaction(
                    db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    val chatEntity = ChatEntity.findById(chatId)!!.load(ChatEntity::admin)
                    if (chatEntity.admin!!.email == principal.name) {
                        proceed()
                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }
                }

            } else if (call.request.path().equals("/api/v1/chat-images/make-preview", false)) {
                val principal = call.principal<UserPasswordCredential>()!!
                val imageRequest = call.receive(ImageRequest::class)

                newSuspendedTransaction(
                    db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    val chatEntity = ChatEntity.findById(imageRequest.entityId)!!.load(ChatEntity::admin)
                    if (chatEntity.admin!!.email == principal.name) {
                        call.attributes[AttributeKey<ImageRequest>("imageRequest")] = imageRequest
                        proceed()
                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }
                }

            } else {
                proceed()
            }
        }

        route("/api/v1/chat-images") {

            get("/by-id") {
                val imageId = UUID.fromString(call.parameters["imageId"])
                val imageEntity = chatImageService.findEntity(imageId)
                call.respondBytes(
                    ContentType.parse(imageEntity.contentType),
                    HttpStatusCode.OK
                ) { imageEntity.data.bytes }
            }

            authenticate("basic-auth-all") {

                post("/create") {
                    val chatId = UUID.fromString(call.parameters["chatId"])
                    chatImageService.create(chatId, call.receiveMultipart(Long.MAX_VALUE))
                    call.respond(HttpStatusCode.OK)
                }

                delete("/update") {
                    val chatId = UUID.fromString(call.parameters["chatId"])
                    val documentRequest = call.receive(DocumentRequest::class)
                    chatImageService.delete(chatId, documentRequest)
                    call.respond(HttpStatusCode.OK)
                }

                post("/make-preview") {
                    val imageRequest = call.attributes[AttributeKey<ImageRequest>("imageRequest")]
                    chatImageService.makePreview(imageRequest)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}