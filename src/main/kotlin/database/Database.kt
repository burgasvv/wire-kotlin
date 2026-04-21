package org.burgas.database

import io.ktor.server.config.ApplicationConfig
import org.jetbrains.exposed.sql.Database
import redis.clients.jedis.Jedis

@Suppress("unused")
class DatabaseConnection {

    companion object {
        val config = ApplicationConfig("application.yaml")

        val postgres = Database.connect(
            driver = config.property("ktor.postgres.driver").getString(),
            url = config.property("ktor.postgres.url").getString(),
            user = config.property("ktor.postgres.user").getString(),
            password = config.property("ktor.postgres.password").getString()
        )

        val redis = Jedis(
            config.property("ktor.redis.host").getString(),
            config.property("ktor.redis.port").getString().toInt()
        )
    }
}