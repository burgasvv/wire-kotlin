package org.burgas.router

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.burgas.dao.IdentityEntity
import org.burgas.dao.PublicationEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.PublicationRequest
import org.burgas.service.PublicationService
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

fun Application.configurePublicationRouter() {

    val publicationService = PublicationService()

    routing {

        @Suppress("DEPRECATION")
        intercept(ApplicationCallPipeline.Call) {
            if (
                call.request.path().equals("/api/v1/publications/by-id", false) ||
                call.request.path().equals("/api/v1/publications/delete", false)
            ) {
                val principal = call.principal<UserPasswordCredential>()!!
                val publicationId = UUID.fromString(call.parameters["publicationId"])

                newSuspendedTransaction(
                    db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    val publicationEntity = PublicationEntity.findById(publicationId)!!.load(PublicationEntity::sender)
                    if (publicationEntity.sender!!.email == principal.name) {
                        proceed()
                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }
                }

            } else if (call.request.path().equals("/api/v1/publications/create", false)) {
                val principal = call.principal<UserPasswordCredential>()!!
                val multiPartData = call.receiveMultipart(Long.MAX_VALUE)
                val publicationPart = multiPartData.readPart()!!

                if (publicationPart is PartData.FormItem && publicationPart.name == "publicationRequest") {
                    val publicationRequest = Json.decodeFromString<PublicationRequest>(publicationPart.value)
                    val senderId = publicationRequest.senderId!!

                    val identityEntity = newSuspendedTransaction(
                        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
                    ) {
                        IdentityEntity.findById(senderId)!!
                    }
                    val files: MutableList<PartData> = mutableListOf()
                    multiPartData.forEachPart { partData ->
                        if (partData is PartData.FileItem) files.add(partData)
                    }

                    if (identityEntity.email == principal.name) {
                        call.attributes[AttributeKey<PublicationRequest>("publicationRequest")] = publicationRequest
                        call.attributes[AttributeKey<List<PartData>>("files")] = files.toList()
                        proceed()
                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }

                } else {
                    throw IllegalArgumentException("Publication part is not Form Item or have wrong part name")
                }

            } else {
                proceed()
            }
        }

        route("/api/v1/publications") {

            authenticate("basic-auth-all") {

                get("/by-id") {
                    val publicationId = UUID.fromString(call.parameters["publicationId"])
                    call.respond(HttpStatusCode.OK, publicationService.findById(publicationId))
                }

                post("/create") {
                    val publicationRequest = call.attributes[AttributeKey<PublicationRequest>("publicationRequest")]
                    val files = call.attributes[AttributeKey<List<PartData>>("files")]
                    publicationService.create(publicationRequest, files)
                    call.respond(HttpStatusCode.OK)
                }

                delete("/delete") {
                    val publicationId = UUID.fromString(call.parameters["publicationId"])
                    publicationService.delete(publicationId)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}