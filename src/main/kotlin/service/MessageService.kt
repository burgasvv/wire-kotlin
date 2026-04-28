package org.burgas.service

import io.ktor.http.content.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.burgas.cache.CacheKey
import org.burgas.cache.RedisCacheHandler
import org.burgas.dao.MessageEntity
import org.burgas.dao.MessageFileEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.MessageFullResponse
import org.burgas.service.dao.DesignDaoPart
import org.burgas.service.dao.ReadDao
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.*

class MessageService : ReadDao<UUID, MessageEntity, MessageFullResponse>, DesignDaoPart<UUID, MessageFullResponse>,
    RedisCacheHandler<MessageEntity> {

    override suspend fun handleCache(entity: MessageEntity) {
        val redis = DatabaseConnection.redis
        val messageKey = CacheKey.MESSAGE_KEY.format(entity.id.value)
        if (redis.exists(messageKey)) redis.del(messageKey)

        val sender = entity.sender
        if (sender != null) {
            val senderKey = CacheKey.IDENTITY_KEY.format(sender.id.value)
            if (redis.exists(senderKey)) redis.del(senderKey)
        }

        val chat = entity.chat
        val chatKey = CacheKey.CHAT_KEY.format(chat.id.value)
        if (redis.exists(chatKey)) redis.del(chatKey)
    }

    override suspend fun findEntity(id: UUID): MessageEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        MessageEntity.findById(id)!!
            .load(MessageEntity::chat, MessageEntity::sender, MessageEntity::files)
    }

    override suspend fun findById(id: UUID): MessageFullResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        val redis = DatabaseConnection.redis
        val messageKey = CacheKey.MESSAGE_KEY.format(id)
        if (redis.exists(messageKey)) {
            Json.decodeFromString<MessageFullResponse>(redis.get(messageKey))
        } else {
            val messageFullResponse = findEntity(id).toFullResponse()
            redis.set(messageKey, Json.encodeToString(messageFullResponse))
            messageFullResponse
        }
    }

    override suspend fun create(multiPartData: MultiPartData): MessageFullResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val readPart = multiPartData.readPart()!!
        val messageEntity = MessageEntity.new { this.insert(readPart) }
        handleCache(messageEntity)
        multiPartData.forEachPart { partData ->
            if (partData is PartData.FileItem) MessageFileEntity.new { this.upload(messageEntity, partData) }
        }
        val messageFullResponse = messageEntity.toFullResponse()
        val messageKey = CacheKey.MESSAGE_KEY.format(messageFullResponse.id)
        DatabaseConnection.redis.set(messageKey, Json.encodeToString(messageFullResponse))
        messageFullResponse
    }

    override suspend fun delete(id: UUID) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val messageEntity = findEntity(id)
        handleCache(messageEntity)
        messageEntity.delete()
    }
}