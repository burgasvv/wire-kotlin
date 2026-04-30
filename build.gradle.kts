
plugins {
    kotlin("jvm") version "2.3.0"
    id("io.ktor.plugin") version "3.4.2"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0"
}

group = "org.burgas"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(24)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-netty")
    implementation("ch.qos.logback:logback-classic:1.5.32")
    implementation("io.ktor:ktor-server-config-yaml")
    implementation("org.jetbrains.exposed:exposed-core:0.61.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.61.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.61.0")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:0.61.0")
    implementation("org.postgresql:postgresql:42.7.10")
    implementation("redis.clients:jedis:5.1.2")
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("io.ktor:ktor-server-sessions:3.4.2")
    implementation("io.ktor:ktor-server-status-pages:3.4.2")
    implementation("io.ktor:ktor-server-cors:3.4.2")
    implementation("io.ktor:ktor-server-csrf:3.4.2")
    implementation("io.ktor:ktor-server-auth:3.4.2")
    implementation("io.ktor:ktor-server-sse:3.4.2")
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.3.20")
}