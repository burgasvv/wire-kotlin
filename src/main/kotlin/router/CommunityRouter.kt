package org.burgas.router

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import org.burgas.dao.CommunityEntity
import org.burgas.dao.IdentityEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.CommunityRequest
import org.burgas.dto.GroupRequest
import org.burgas.service.CommunityService
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

fun Application.configureCommunityRouter() {

    val communityService = CommunityService()

    routing {

        @Suppress("DEPRECATION")
        intercept(ApplicationCallPipeline.Call) {

            if (call.request.path().equals("/api/v1/communities/create", false)) {
                val principal = call.principal<UserPasswordCredential>()!!
                val communityRequest = call.receive(CommunityRequest::class)
                val adminId = communityRequest.adminId!!

                val identityEntity = newSuspendedTransaction(
                    db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    IdentityEntity.findById(adminId)!!
                }
                if (identityEntity.email == principal.name) {
                    call.attributes[AttributeKey<CommunityRequest>("communityRequest")] = communityRequest
                    proceed()
                } else {
                    throw IllegalArgumentException("Identity not authorized")
                }

            } else if (call.request.path().equals("/api/v1/communities/update", false)) {
                val principal = call.principal<UserPasswordCredential>()!!
                val communityRequest = call.receive(CommunityRequest::class)
                val communityId = communityRequest.id!!

                newSuspendedTransaction(
                    db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    val communityEntity = CommunityEntity.findById(communityId)!!.load(CommunityEntity::admin)
                    if (communityEntity.admin!!.email == principal.name) {
                        call.attributes[AttributeKey<CommunityRequest>("communityRequest")] = communityRequest
                        proceed()
                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }
                }

            } else if (call.request.path().equals("/api/v1/communities/delete", false)) {
                val principal = call.principal<UserPasswordCredential>()!!
                val communityId = UUID.fromString(call.parameters["communityId"])

                newSuspendedTransaction(
                    db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    val communityEntity = CommunityEntity.findById(communityId)!!.load(CommunityEntity::admin)
                    if (communityEntity.admin!!.email == principal.name) {
                        proceed()
                    } else {
                        throw IllegalArgumentException("Identity not authorized")
                    }
                }

            } else if (
                call.request.path().equals("/api/v1/communities/join", false) ||
                call.request.path().equals("/api/v1/communities/out", false)
            ) {
                val principal = call.principal<UserPasswordCredential>()!!
                val groupRequest = call.receive(GroupRequest::class)

                val identityEntity = newSuspendedTransaction(
                    db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    IdentityEntity.findById(groupRequest.applicantId)!!
                }
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

        route("/api/v1/communities") {

            get("/by-id") {
                val communityId = UUID.fromString(call.parameters["communityId"])
                call.respond(HttpStatusCode.OK, communityService.findById(communityId))
            }

            authenticate("basic-auth-admin") {

                get {
                    call.respond(HttpStatusCode.OK, communityService.findAll())
                }
            }

            authenticate("basic-auth-all") {

                post("/create") {
                    val communityRequest = call.attributes[AttributeKey<CommunityRequest>("communityRequest")]
                    val communityFullResponse = communityService.create(communityRequest)
                    call.respondRedirect("/api/v1/communities/by-id?communityId=${communityFullResponse.id}")
                }

                post("/update") {
                    val communityRequest = call.attributes[AttributeKey<CommunityRequest>("communityRequest")]
                    val communityFullResponse = communityService.update(communityRequest)
                    call.respondRedirect("/api/v1/communities/by-id?communityId=${communityFullResponse.id}")
                }

                delete("/delete") {
                    val communityId = UUID.fromString(call.parameters["communityId"])
                    communityService.delete(communityId)
                    call.respond(HttpStatusCode.OK)
                }

                post("/join") {
                    val groupRequest = call.attributes[AttributeKey<GroupRequest>("groupRequest")]
                    communityService.join(groupRequest)
                    call.respond(HttpStatusCode.OK)
                }

                post("/out") {
                    val groupRequest = call.attributes[AttributeKey<GroupRequest>("groupRequest")]
                    communityService.out(groupRequest)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}