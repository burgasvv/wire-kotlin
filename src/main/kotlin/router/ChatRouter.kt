package org.burgas.router

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import org.burgas.dao.ChatEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.ChatRequest
import org.burgas.dto.GroupRequest
import org.burgas.service.ChatService
import org.burgas.service.IdentityService
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

fun Application.configureChatRouter() {

    val identityService = IdentityService()
    val chatService = ChatService()

    routing {

        @Suppress("DEPRECATION")
        intercept(ApplicationCallPipeline.Call) {

            if (call.request.path().equals("/api/v1/chats/create", false)) {
                val principal = call.principal<UserPasswordCredential>()!!
                val chatRequest = call.receive(ChatRequest::class)
                val adminId = chatRequest.adminId!!

                val identityEntity = identityService.findEntity(adminId)
                if (identityEntity.email == principal.name) {
                    call.attributes[AttributeKey<ChatRequest>("chatRequest")] = chatRequest
                    proceed()
                } else {
                    throw IllegalArgumentException("Identity not authorized")
                }

            } else if (call.request.path().equals("/api/v1/chats/update", false)) {
                val principal = call.principal<UserPasswordCredential>()!!
                val chatRequest = call.receive(ChatRequest::class)

                newSuspendedTransaction(
                    db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    val chatEntity = ChatEntity.findById(chatRequest.id!!)!!.load(ChatEntity::admin)
                    if (chatEntity.admin!!.email == principal.name) {
                        call.attributes[AttributeKey<ChatRequest>("chatRequest")] = chatRequest
                        proceed()
                    } else {
                        throw IllegalArgumentException("identity not authorized")
                    }
                }

            } else if (call.request.path().equals("/api/v1/chats/delete", false)) {
                val principal = call.principal<UserPasswordCredential>()!!
                val chatId = UUID.fromString(call.parameters["chatId"])

                newSuspendedTransaction(
                    db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    val chatEntity = ChatEntity.findById(chatId)!!.load(ChatEntity::admin)
                    if (chatEntity.admin!!.email == principal.name) {
                        proceed()
                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }
                }

            } else if (
                call.request.path().equals("/api/v1/chats/join", false) ||
                call.request.path().equals("/api/v1/chats/out", false)
            ) {
                val principal = call.principal<UserPasswordCredential>()!!
                val groupRequest = call.receive(GroupRequest::class)

                val identityEntity = identityService.findEntity(groupRequest.applicantId)
                if (identityEntity.email == principal.name) {
                    call.attributes[AttributeKey<GroupRequest>("groupRequest")] = groupRequest
                    proceed()
                } else {
                    throw IllegalArgumentException("Identity not authorized")
                }

            } else {
                proceed()
            }
        }

        route("/api/v1/chats") {

            get("/by-id") {
                val chatId = UUID.fromString(call.parameters["chatId"])
                call.respond(HttpStatusCode.OK, chatService.findById(chatId))
            }

            authenticate("basic-auth-admin") {

                get {
                    call.respond(HttpStatusCode.OK, chatService.findAll())
                }
            }

            authenticate("basic-auth-all") {

                post("/create") {
                    val chatRequest = call.attributes[AttributeKey<ChatRequest>("chatRequest")]
                    val chatFullResponse = chatService.create(chatRequest)
                    call.respondRedirect("/api/v1/chats/by-id?chatId=${chatFullResponse.id}")
                }

                post("/update") {
                    val chatRequest = call.attributes[AttributeKey<ChatRequest>("chatRequest")]
                    val chatFullResponse = chatService.update(chatRequest)
                    call.respondRedirect("/api/v1/chats/by-id?chatId=${chatFullResponse.id}")
                }

                delete("/delete") {
                    val chatId = UUID.fromString(call.parameters["chatId"])
                    chatService.delete(chatId)
                    call.respond(HttpStatusCode.OK)
                }

                post("/join") {
                    val groupRequest = call.attributes[AttributeKey<GroupRequest>("groupRequest")]
                    chatService.join(groupRequest)
                    call.respond(HttpStatusCode.OK)
                }

                post("/out") {
                    val groupRequest = call.attributes[AttributeKey<GroupRequest>("groupRequest")]
                    chatService.out(groupRequest)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}