package org.burgas.router

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.sse.*
import io.ktor.util.*
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.json.Json
import org.burgas.dao.IdentityEntity
import org.burgas.dao.PublicationEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.PublicationFullResponse
import org.burgas.dto.PublicationRequest
import org.burgas.service.CommunityService
import org.burgas.service.PublicationService
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.time.Duration

fun Application.configurePublicationRouter() {

    val communityService = CommunityService()
    val publicationService = PublicationService()
    val publications = MutableSharedFlow<PublicationFullResponse>(
        replay = Int.MAX_VALUE, onBufferOverflow = BufferOverflow.DROP_LATEST
    )
    val connections: CopyOnWriteArraySet<DefaultWebSocketServerSession> = CopyOnWriteArraySet()

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

            webSocket("/ws/by-community") {
                connections += this
                try {
                    val communityId = UUID.fromString(call.parameters["communityId"])
                    val communityEntity = communityService.findEntity(communityId)
                    val publicationFullResponses = newSuspendedTransaction(
                        db = DatabaseConnection.postgres, context = Dispatchers.Default
                    ) {
                        communityEntity.publications.map { it.toFullResponse() }
                    }
                    publicationFullResponses.forEach { send(Frame.Text(Json.encodeToString(it))) }
                    incoming.receiveAsFlow().filterIsInstance<Frame.Text>()
                        .collect { frameText ->
                            val text = frameText.readText()
                            val publicationFullResponse = Json.decodeFromString<PublicationFullResponse>(text)
                            if (publicationFullResponse.community?.id == communityId) {
                                send(Frame.Text(text))
                            } else {
                                send(Frame.Text("Wrong community for this publication"))
                            }
                        }
                } finally {
                    connections -= this
                }
            }

            authenticate("basic-auth-all") {

                sse("/by-community") {
                    heartbeat { period = Duration.INFINITE }
                    val communityId = UUID.fromString(call.parameters["communityId"])
                    val communityEntity = communityService.findEntity(communityId)
                    val publicationFullResponses = newSuspendedTransaction(
                        db = DatabaseConnection.postgres, context = Dispatchers.Default
                    ) {
                        communityEntity.publications.map { it.toFullResponse() }
                    }
                    publicationFullResponses.forEach { publications.tryEmit(it) }
                    publications.collect {
                        if (it.community?.id == communityEntity.id.value)
                            send(ServerSentEvent(Json.encodeToString(it)))
                    }
                }

                get("/by-id") {
                    val publicationId = UUID.fromString(call.parameters["publicationId"])
                    call.respond(HttpStatusCode.OK, publicationService.findById(publicationId))
                }

                post("/create") {
                    val publicationRequest = call.attributes[AttributeKey<PublicationRequest>("publicationRequest")]
                    val files = call.attributes[AttributeKey<List<PartData>>("files")]
                    val publicationFullResponse = publicationService.create(publicationRequest, files)
                    publications.emit(publicationFullResponse)
                    connections.forEach { defaultWebSocketServerSession ->
                        defaultWebSocketServerSession.send(Frame.Text(Json.encodeToString(publicationFullResponse)))
                    }
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