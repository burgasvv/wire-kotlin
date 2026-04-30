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
import org.burgas.dao.CommentEntity
import org.burgas.dao.IdentityEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.CommentRequest
import org.burgas.service.CommentService
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

fun Application.configureCommentRouter() {

    val commentService = CommentService()

    routing {

        @Suppress("DEPRECATION")
        intercept(ApplicationCallPipeline.Call) {

            if (
                call.request.path().equals("/api/v1/comments/by-id", false) ||
                call.request.path().equals("/api/v1/comments/delete", false)
            ) {
                val principal = call.principal<UserPasswordCredential>()!!
                val commentId = UUID.fromString(call.parameters["commentId"])

                newSuspendedTransaction(
                    db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    val commentEntity = CommentEntity.findById(commentId)!!.load(CommentEntity::sender)
                    if (commentEntity.sender!!.email == principal.name) {
                        proceed()
                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }
                }

            } else if (call.request.path().equals("/api/v1/publications/create", false)) {
                val principal = call.principal<UserPasswordCredential>()!!
                val multiPartData = call.receiveMultipart(Long.MAX_VALUE)
                val commentPart = multiPartData.readPart()!!

                if (commentPart is PartData.FormItem && commentPart.name == "commentRequest") {
                    val commentRequest = Json.decodeFromString<CommentRequest>(commentPart.value)
                    val senderId = commentRequest.senderId!!

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
                        call.attributes[AttributeKey<CommentRequest>("commentRequest")] = commentRequest
                        call.attributes[AttributeKey<List<PartData>>("files")] = files.toList()
                        proceed()
                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }

                } else {
                    throw IllegalArgumentException("Comment part is not Form Item or have wrong part name")
                }

            } else {
                proceed()
            }
        }

        route("/api/v1/comments") {

            authenticate("basic-auth-all") {

                get("/by-id") {
                    val commentId = UUID.fromString(call.parameters["commentId"])
                    call.respond(HttpStatusCode.OK, commentService.findById(commentId))
                }

                post("/create") {
                    val commentRequest = call.attributes[AttributeKey<CommentRequest>("commentRequest")]
                    val files = call.attributes[AttributeKey<List<PartData>>("files")]
                    commentService.create(commentRequest, files)
                    call.respond(HttpStatusCode.OK)
                }

                delete("/delete") {
                    val commentId = UUID.fromString(call.parameters["commentId"])
                    commentService.delete(commentId)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}