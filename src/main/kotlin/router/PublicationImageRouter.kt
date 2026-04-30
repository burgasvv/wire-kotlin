package org.burgas.router

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.burgas.service.PublicationImageService
import java.util.UUID

fun Application.configurePublicationImageRouter() {

    val publicationImageService = PublicationImageService()

    routing {

        route("/api/v1/publication-images") {

            get("/by-id") {
                val imageId = UUID.fromString(call.parameters["imageId"])
                val publicationImageEntity = publicationImageService.findEntity(imageId)
                call.respondBytes(ContentType.parse(publicationImageEntity.contentType), HttpStatusCode.OK) {
                    publicationImageEntity.data.bytes
                }
            }
        }

    }
}