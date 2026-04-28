package org.burgas

import io.ktor.server.application.*
import org.burgas.database.configureDatabase
import org.burgas.router.configureChatImageRouter
import org.burgas.router.configureChatRouter
import org.burgas.router.configureIdentityImageRouter
import org.burgas.router.configureIdentityRouter
import org.burgas.router.configureMessageRouter
import org.burgas.router.configureSecurityRouter
import org.burgas.security.configureSecurity
import org.burgas.serialization.configureSerialization

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureSecurity()
    configureDatabase()

    configureSecurityRouter()
    configureIdentityRouter()
    configureIdentityImageRouter()
    configureChatRouter()
    configureChatImageRouter()
    configureMessageRouter()
}
