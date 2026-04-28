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
import org.burgas.dto.IdentityRequest
import org.burgas.service.IdentityService
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

fun Application.configureIdentityRouter() {

    val identityService = IdentityService()

    routing {

        @Suppress("DEPRECATION")
        intercept(ApplicationCallPipeline.Call) {

            if (call.request.path().equals("/api/v1/identities/delete", false)) {
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
            } else if (
                call.request.path().equals("/api/v1/identities/update", false) ||
                call.request.path().equals("/api/v1/identities/change-password", false)
            ) {
                val principal = call.principal<UserPasswordCredential>()!!
                val identityRequest = call.receive(IdentityRequest::class)
                val identityId = identityRequest.id!!

                val identityEntity = newSuspendedTransaction(
                    db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    IdentityEntity.findById(identityId)!!
                }
                if (identityEntity.email == principal.name) {
                    call.attributes[AttributeKey<IdentityRequest>("identityRequest")] = identityRequest
                    proceed()
                } else {
                    throw IllegalArgumentException("Identity not authorized")
                }

            } else {
                proceed()
            }
        }

        route("/api/v1/identities") {

            get("/by-id") {
                val identityId = UUID.fromString(call.parameters["identityId"])
                call.respond(HttpStatusCode.OK, identityService.findById(identityId))
            }

            post("/create") {
                val identityRequest = call.receive(IdentityRequest::class)
                val identityFullResponse = identityService.create(identityRequest)
                call.respondRedirect("/api/v1/identities/by-id?identityId=${identityFullResponse.id}")
            }

            authenticate("basic-auth-admin") {

                get {
                    call.respond(HttpStatusCode.OK, identityService.findAll())
                }

                post("/change-status") {
                    val identityRequest = call.receive(IdentityRequest::class)
                    identityService.changeStatus(identityRequest)
                    call.respond(HttpStatusCode.OK)
                }
            }

            authenticate("basic-auth-all") {

                post("/update") {
                    val identityRequest = call.attributes[AttributeKey<IdentityRequest>("identityRequest")]
                    val identityFullResponse = identityService.update(identityRequest)
                    call.respondRedirect("/api/v1/identities/by-id?identityId=${identityFullResponse.id}")
                }

                delete("/delete") {
                    val identityId = UUID.fromString(call.parameters["identityId"])
                    identityService.delete(identityId)
                    call.respond(HttpStatusCode.OK)
                }

                post("/change-password") {
                    val identityRequest = call.attributes[AttributeKey<IdentityRequest>("identityRequest")]
                    val identityFullResponse = identityService.changePassword(identityRequest)
                    call.respondRedirect("/api/v1/identities/by-id?identityId=${identityFullResponse.id}")
                }
            }
        }
    }
}