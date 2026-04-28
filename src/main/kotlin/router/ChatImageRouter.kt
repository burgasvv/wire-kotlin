package org.burgas.router

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.auth.UserPasswordCredential
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.intercept
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.AttributeKey
import org.burgas.dto.DocumentRequest
import org.burgas.dto.ImageRequest
import org.burgas.service.ChatImageService
import org.burgas.service.IdentityService
import java.util.UUID

fun Application.configureChatImageRouter() {

    val identityService = IdentityService()
    val chatImageService = ChatImageService()

    routing {

        @Suppress("DEPRECATION")
        intercept(ApplicationCallPipeline.Call) {
            if (
                call.request.path().equals("/api/v1/chat-images/create", false) ||
                call.request.path().equals("/api/v1/chat-images/delete", false)
            ) {
                val principal = call.principal<UserPasswordCredential>()!!
                val identityId = UUID.fromString(call.parameters["identityId"])

                val identityEntity = identityService.findEntity(identityId)
                if (identityEntity.email == principal.name) {
                    proceed()
                } else {
                    throw IllegalArgumentException("Identity not authorized")
                }

            } else if (call.request.path().equals("/api/v1/chat-images/make-preview", false)) {
                val principal = call.principal<UserPasswordCredential>()!!
                val imageRequest = call.receive(ImageRequest::class)
                val identityId = imageRequest.entityId

                val identityEntity = identityService.findEntity(identityId)
                if (identityEntity.email == principal.name) {
                    call.attributes[AttributeKey<ImageRequest>("imageRequest")] = imageRequest
                    proceed()
                } else {
                    throw IllegalArgumentException("Identity not authorized")
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
                    chatImageService.create(chatId, call.receiveMultipart())
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