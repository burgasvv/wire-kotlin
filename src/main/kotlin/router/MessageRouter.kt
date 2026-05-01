package org.burgas.router

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.sse.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.Json
import org.burgas.dao.IdentityEntity
import org.burgas.dao.MessageEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.MessageFullResponse
import org.burgas.dto.MessageRequest
import org.burgas.service.ChatService
import org.burgas.service.MessageService
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*
import kotlin.time.Duration

fun Application.configureMessageRouter() {

    val chatService = ChatService()
    val messageService = MessageService()
    val messages = MutableSharedFlow<MessageFullResponse>(
        replay = Int.MAX_VALUE, onBufferOverflow = BufferOverflow.DROP_LATEST
    )

    routing {

        @Suppress("DEPRECATION")
        intercept(ApplicationCallPipeline.Call) {
            if (
                call.request.path().equals("/api/v1/messages/by-id", false) ||
                call.request.path().equals("/api/v1/messages/delete", false)
            ) {
                val principal = call.principal<UserPasswordCredential>()!!
                val messageId = UUID.fromString(call.parameters["messageId"])

                newSuspendedTransaction(
                    db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    val messageEntity = MessageEntity.findById(messageId)!!
                    if (messageEntity.sender!!.email == principal.name) {
                        proceed()
                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }
                }

            } else if (call.request.path().equals("/api/v1/messages/create", false)) {
                val principal = call.principal<UserPasswordCredential>()!!
                val multiPartData = call.receiveMultipart(Long.MAX_VALUE)
                val messagePart = multiPartData.readPart()!!

                if (messagePart is PartData.FormItem && messagePart.name == "messageRequest") {
                    val messageRequest = Json.decodeFromString<MessageRequest>(messagePart.value)
                    val senderId = messageRequest.senderId!!

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
                        call.attributes[AttributeKey<MessageRequest>("messageRequest")] = messageRequest
                        call.attributes[AttributeKey<List<PartData>>("files")] = files.toList()
                        proceed()
                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }

                } else {
                    throw IllegalArgumentException("Message part is not Form Item or have wrong part name")
                }

            } else {
                proceed()
            }
        }

        route("/api/v1/messages") {

            authenticate("basic-auth-all") {

                sse("/by-chat") {
                    heartbeat { period = Duration.INFINITE }
                    val chatId = UUID.fromString(call.parameters["chatId"])
                    val chatEntity = chatService.findEntity(chatId)
                    val messageFullResponses = newSuspendedTransaction(
                        db = DatabaseConnection.postgres, context = Dispatchers.Default
                    ) {
                        chatEntity.messages.map { it.toFullResponse() }
                    }
                    messageFullResponses.forEach { messages.tryEmit(it) }
                    messages.collect {
                        if (it.chat?.id == chatEntity.id.value)
                            send(ServerSentEvent(Json.encodeToString(it)))
                    }
                }

                get("/by-id") {
                    val messageId = UUID.fromString(call.parameters["messageId"])
                    call.respond(HttpStatusCode.OK, messageService.findById(messageId))
                }

                post("/create") {
                    val messageRequest = call.attributes[AttributeKey<MessageRequest>("messageRequest")]
                    val files = call.attributes[AttributeKey<List<PartData>>("files")]
                    val messageFullResponse = messageService.create(messageRequest, files)
                    messages.emit(messageFullResponse)
                    call.respond(HttpStatusCode.OK)
                }

                delete("/delete") {
                    val messageId = UUID.fromString(call.parameters["messageId"])
                    messageService.delete(messageId)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}