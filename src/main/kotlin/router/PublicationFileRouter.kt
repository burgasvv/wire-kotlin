package org.burgas.router

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.burgas.service.PublicationFileService
import java.util.UUID

fun Application.configurePublicationFileRouter() {

    val publicationFileService = PublicationFileService()

    routing {

        route("/api/v1/publication-files") {

            get("/by-id") {
                val fileId = UUID.fromString(call.parameters["fileId"])
                val publicationFileEntity = publicationFileService.findEntity(fileId)
                call.respondBytes(ContentType.parse(publicationFileEntity.contentType), HttpStatusCode.OK) {
                    publicationFileEntity.data.bytes
                }
            }
        }
    }
}