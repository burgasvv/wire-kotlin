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
import kotlinx.coroutines.Dispatchers
import org.burgas.dao.CommunityEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.DocumentRequest
import org.burgas.dto.ImageRequest
import org.burgas.service.CommunityImageService
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

fun Application.configureCommunityImageRouter() {

    val communityImageService = CommunityImageService()

    routing {

        @Suppress("DEPRECATION")
        intercept(ApplicationCallPipeline.Call) {

            if (
                call.request.path().equals("/api/v1/community-images/create", false) ||
                call.request.path().equals("/api/v1/community-images/delete", false)
            ) {
                val principal = call.principal<UserPasswordCredential>()!!
                val communityId = UUID.fromString(call.parameters["communityId"])

                newSuspendedTransaction(
                    db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    val communityEntity = CommunityEntity.findById(communityId)!!
                    if (communityEntity.admin!!.email == principal.name) {
                        proceed()
                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }
                }

            } else if (call.request.path().equals("/api/v1/community-images/make-preview", false)) {
                val principal = call.principal<UserPasswordCredential>()!!
                val imageRequest = call.receive(ImageRequest::class)

                newSuspendedTransaction(
                    db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    val communityEntity = CommunityEntity.findById(imageRequest.entityId)!!
                    if (communityEntity.admin!!.email == principal.name) {
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

        route("/api/v1/community-images") {

            get("/by-id") {
                val imageId = UUID.fromString(call.parameters["imageId"])
                val communityImageEntity = communityImageService.findEntity(imageId)
                call.respondBytes(ContentType.parse(communityImageEntity.contentType), HttpStatusCode.OK) {
                    communityImageEntity.data.bytes
                }
            }

            authenticate("basic-auth-all") {

                post("/create") {
                    val communityId = UUID.fromString(call.parameters["communityId"])
                    communityImageService.create(communityId, call.receiveMultipart(Long.MAX_VALUE))
                    call.respond(HttpStatusCode.OK)
                }

                delete("/delete") {
                    val communityId = UUID.fromString(call.parameters["communityId"])
                    val documentRequest = call.receive(DocumentRequest::class)
                    communityImageService.delete(communityId, documentRequest)
                    call.respond(HttpStatusCode.OK)
                }

                post("/make-preview") {
                    val imageRequest = call.attributes[AttributeKey<ImageRequest>("imageRequest")]
                    communityImageService.makePreview(imageRequest)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}