package org.burgas.security

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.csrf.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import org.burgas.dao.IdentityEntity
import org.burgas.database.Authority
import org.burgas.database.IdentityTable
import org.burgas.dto.CsrfToken
import org.burgas.dto.ExceptionResponse
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.mindrot.jbcrypt.BCrypt

fun Application.configureSecurity() {

    authentication {
        basic(name = "basic-auth-all") {
            validate { credentials ->
                val identityEntity = newSuspendedTransaction {
                    IdentityEntity.find { IdentityTable.email eq credentials.name }.singleOrNull()
                }
                if (
                    identityEntity != null && identityEntity.status &&
                    BCrypt.checkpw(credentials.password, identityEntity.password)
                ) {
                    UserPasswordCredential(credentials.name, credentials.password)
                } else {
                    null
                }
            }
        }
        basic(name = "basic-auth-admin") {
            validate { credentials ->
                val identityEntity = newSuspendedTransaction {
                    IdentityEntity.find { IdentityTable.email eq credentials.name }.singleOrNull()
                }
                if (
                    identityEntity != null && identityEntity.status && identityEntity.authority == Authority.ADMIN &&
                    BCrypt.checkpw(credentials.password, identityEntity.password)
                ) {
                    UserPasswordCredential(credentials.name, credentials.password)
                } else {
                    null
                }
            }
        }
    }

//    install(StatusPages) {
//        exception<Throwable> { call, cause ->
//            val exceptionResponse = ExceptionResponse(
//                status = HttpStatusCode.BadRequest.description,
//                code = HttpStatusCode.BadRequest.value,
//                message = cause.localizedMessage
//            )
//            call.respond(HttpStatusCode.BadRequest, exceptionResponse)
//        }
//    }

    install(Sessions) {
        cookie<CsrfToken>("CSRF_TOKEN")
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)

        allowHeader(HttpHeaders.Host)
        allowHeader(HttpHeaders.Accept)
        allowHeader(HttpHeaders.Origin)
        allowHeader("X-CSRF-Token")
        allowHeader(HttpHeaders.Authorization)

        allowHost("localhost:4200")
    }


    install(CSRF) {
        allowOrigin("http://localhost:9000")
        originMatchesHost()
        checkHeader("X-CSRF-Token")
    }
}