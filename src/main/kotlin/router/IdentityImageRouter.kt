package org.burgas.router

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.AttributeKey
import kotlinx.coroutines.Dispatchers
import org.burgas.dao.IdentityEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.DocumentRequest
import org.burgas.dto.ImageRequest
import org.burgas.service.IdentityImageService
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

fun Application.configureIdentityImageRouter() {

    val identityImageService = IdentityImageService()

    routing {

        @Suppress("DEPRECATION")
        intercept(ApplicationCallPipeline.Call) {
            if (
                call.request.path().equals("/api/v1/identity-images/create", false) ||
                call.request.path().equals("/api/v1/identity-images/delete", false)
            ) {
                val principal = call.principal<UserPasswordCredential>()!!
                val identityId = UUID.fromString(call.parameters["identityId"])

                val identityEntity = newSuspendedTransaction(
                    db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    IdentityEntity.findById(identityId)!!
                }
                if (identityEntity.email == principal.name) {
                    proceed()
                } else {
                    throw IllegalArgumentException("Identity not authorized")
                }

            } else if (call.request.path().equals("/api/v1/identity-images/make-preview", false)) {
                val principal = call.principal<UserPasswordCredential>()!!
                val imageRequest = call.receive(ImageRequest::class)
                val identityId = imageRequest.entityId

                newSuspendedTransaction(
                    db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    val identityEntity = IdentityEntity.findById(identityId)!!
                    if (identityEntity.email == principal.name) {
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

        route("/api/v1/identity-images") {

            get("/by-id") {
                val imageId = UUID.fromString(call.parameters["imageId"])
                val imageEntity = identityImageService.findEntity(imageId)
                call.respondBytes(
                    ContentType.parse(imageEntity.contentType),
                    HttpStatusCode.OK
                ) { imageEntity.data.bytes }
            }

            authenticate("basic-auth-all") {

                post("/create") {
                    val identityId = UUID.fromString(call.parameters["identityId"])
                    identityImageService.create(identityId, call.receiveMultipart())
                    call.respond(HttpStatusCode.OK)
                }

                delete("/delete") {
                    val identityId = UUID.fromString(call.parameters["identityId"])
                    val documentRequest = call.receive(DocumentRequest::class)
                    identityImageService.delete(identityId, documentRequest)
                    call.respond(HttpStatusCode.OK)
                }

                post("/make-preview") {
                    val imageRequest = call.attributes[AttributeKey<ImageRequest>("imageRequest")]
                    val identityImageEntity = identityImageService.makePreview(imageRequest)
                    call.respondRedirect("/api/v1/identities/by-id?identityId=${identityImageEntity.identity.id.value}")
                }
            }
        }
    }
}