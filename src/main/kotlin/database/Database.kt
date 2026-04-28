package org.burgas.database

import io.ktor.server.application.*
import io.ktor.server.config.*
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentDateTime
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import redis.clients.jedis.Jedis
import java.sql.Connection

@Suppress("unused")
class DatabaseConnection {

    companion object {
        private val config = ApplicationConfig("application.yaml")

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

@Suppress("unused")
enum class Authority {
    ADMIN, USER
}

object IdentityTable : UUIDTable("identity") {
    val authority = enumerationByName<Authority>("authority", 250)
    val username = varchar("username", 250).uniqueIndex()
    val password = varchar("password", 250)
    val email = varchar("email", 250).uniqueIndex()
    val status = bool("status").default(true)
    val firstname = varchar("firstname", 250)
    val lastname = varchar("lastname", 250)
    val patronymic = varchar("patronymic", 250)
}

object IdentityImageTable : UUIDTable("identity_image") {
    val name = varchar("name", 250)
    val contentType = varchar("content_type", 250)
    val preview = bool("preview").default(false)
    val data = blob("data")
    val identityId = reference(
        name = "identity_id", refColumn = IdentityTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
}

object ChatTable : UUIDTable("chat") {
    val name = varchar("name", 250)
    val description = text("description")
    val adminId = optReference(
        name = "admin_id", refColumn = IdentityTable.id,
        onDelete = ReferenceOption.SET_NULL, onUpdate = ReferenceOption.CASCADE
    )
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}

object ChatImageTable : UUIDTable("chat_image") {
    val name = varchar("name", 250)
    val contentType = varchar("content_type", 250)
    val preview = bool("preview").default(false)
    val data = blob("data")
    val chatId = reference(
        name = "chat_id", refColumn = ChatTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
}

object IdentityChatTable : Table("identity_chat") {
    val identityId = reference(
        name = "identity_id", refColumn = IdentityTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    val chatId = reference(
        name = "chat_id", refColumn = ChatTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    override val primaryKey: PrimaryKey
        get() = PrimaryKey(arrayOf(identityId, chatId))
}

object MessageTable : UUIDTable("message") {
    val chatId = reference(
        name = "chat_id", refColumn = ChatTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    val senderId = optReference(
        name = "sender_id", refColumn = IdentityTable.id,
        onDelete = ReferenceOption.SET_NULL, onUpdate = ReferenceOption.CASCADE
    )
    val text = text("text")
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}

object MessageFileTable : UUIDTable("message_file") {
    val name = varchar("name", 250)
    val contentType = varchar("content_type", 250)
    val data = blob("data")
    val messageId = reference(
        name = "message_id", refColumn = MessageTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
}

object CommunityTable : UUIDTable("community") {
    val name = varchar("name", 250).uniqueIndex()
    val description = text("description")
    val adminId = optReference(
        name = "admin_id", refColumn = IdentityTable.id,
        onDelete = ReferenceOption.SET_NULL, onUpdate = ReferenceOption.CASCADE
    )
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}

object CommunityImageTable : UUIDTable("community_image") {
    val name = varchar("name", 250)
    val contentType = varchar("content_type", 250)
    val preview = bool("preview").default(false)
    val data = blob("data")
    val communityId = reference(
        name = "community_id", refColumn = CommunityTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
}

object IdentityCommunityTable : Table("identity_community") {
    val identityId = reference(
        name = "identity_id", refColumn = IdentityTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    val communityId = reference(
        name = "community_id", refColumn = CommunityTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    override val primaryKey: PrimaryKey
        get() = PrimaryKey(arrayOf(identityId, communityId))
}

object PublicationTable : UUIDTable("publication") {
    val communityId = reference(
        name = "community_id", refColumn = CommunityTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    val senderId = optReference(
        name = "sender_id", refColumn = IdentityTable.id,
        onDelete = ReferenceOption.SET_NULL, onUpdate = ReferenceOption.CASCADE
    )
    val text = text("text")
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}

object PublicationFileTable : UUIDTable("publication_file") {
    val name = varchar("name", 250)
    val contentType = varchar("content_type", 250)
    val data = blob("data")
    val publicationId = reference(
        name = "publication_id", refColumn = PublicationTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
}

object PublicationImageTable : UUIDTable("publication_image") {
    val name = varchar("name", 250)
    val contentType = varchar("content_type", 250)
    val preview = bool("preview").default(false)
    val data = blob("data")
    val publicationId = reference(
        name = "publication_id", refColumn = PublicationTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
}

object CommentTable : UUIDTable("comment") {
    val publicationId = reference(
        name = "publication_id", refColumn = PublicationTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    val senderId = optReference(
        name = "sender_id", refColumn = IdentityTable.id,
        onDelete = ReferenceOption.SET_NULL, onUpdate = ReferenceOption.CASCADE
    )
    val text = text("text")
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}

object CommentFileTable : UUIDTable("comment_file") {
    val name = varchar("name", 250)
    val contentType = varchar("content_type", 250)
    val data = blob("data")
    val commentId = reference(
        name = "comment_id", refColumn = CommentTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
}

@Suppress("UnusedReceiverParameter")
fun Application.configureDatabase() {
    transaction(db = DatabaseConnection.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
        SchemaUtils.create(
            IdentityTable, IdentityImageTable, ChatTable, ChatImageTable, IdentityChatTable, MessageTable,
            MessageFileTable, CommunityTable, CommunityImageTable, IdentityCommunityTable, PublicationTable,
            PublicationFileTable, PublicationImageTable, CommentTable, CommentFileTable
        )
    }
}