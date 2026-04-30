package org.burgas

import io.ktor.server.application.*
import org.burgas.database.configureDatabase
import org.burgas.router.configureChatImageRouter
import org.burgas.router.configureChatRouter
import org.burgas.router.configureCommentFileRouter
import org.burgas.router.configureCommentRouter
import org.burgas.router.configureCommunityImageRouter
import org.burgas.router.configureCommunityRouter
import org.burgas.router.configureIdentityImageRouter
import org.burgas.router.configureIdentityRouter
import org.burgas.router.configureMessageFileRouter
import org.burgas.router.configureMessageRouter
import org.burgas.router.configurePublicationFileRouter
import org.burgas.router.configurePublicationImageRouter
import org.burgas.router.configurePublicationRouter
import org.burgas.router.configureSecurityRouter
import org.burgas.security.configureSecurity
import org.burgas.serialization.configureSerialization
import org.burgas.sse.configureServerSentEvent

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureSecurity()
    configureDatabase()
    configureServerSentEvent()

    configureSecurityRouter()

    configureIdentityRouter()
    configureIdentityImageRouter()

    configureChatRouter()
    configureChatImageRouter()

    configureMessageRouter()
    configureMessageFileRouter()

    configureCommunityRouter()
    configureCommunityImageRouter()

    configurePublicationRouter()
    configurePublicationFileRouter()
    configurePublicationImageRouter()

    configureCommentRouter()
    configureCommentFileRouter()
}
