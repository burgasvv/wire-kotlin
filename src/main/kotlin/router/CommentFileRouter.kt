package org.burgas.router

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.burgas.service.CommentFileService
import java.util.UUID

fun Application.configureCommentFileRouter() {

    val commentFileService = CommentFileService()

    routing {

        route("/api/v1/comment-files") {

            get("/by-id") {
                val fileId = UUID.fromString(call.parameters["fileId"])
                val commentFileEntity = commentFileService.findEntity(fileId)
                call.respondBytes(ContentType.parse(commentFileEntity.contentType), HttpStatusCode.OK) {
                    commentFileEntity.data.bytes
                }
            }
        }
    }
}