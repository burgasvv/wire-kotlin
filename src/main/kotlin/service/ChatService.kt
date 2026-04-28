package org.burgas.service

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.burgas.cache.CacheKey
import org.burgas.cache.RedisCacheHandler
import org.burgas.dao.ChatEntity
import org.burgas.database.DatabaseConnection
import org.burgas.database.IdentityChatTable
import org.burgas.dto.ChatFullResponse
import org.burgas.dto.ChatRequest
import org.burgas.dto.ChatShortResponse
import org.burgas.dto.GroupRequest
import org.burgas.service.dao.*
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.*

class ChatService : ListDao<ChatShortResponse>, ReadDao<UUID, ChatEntity, ChatFullResponse>,
    DesignDao<UUID, ChatRequest, ChatFullResponse>, ModifyDao<ChatRequest, ChatFullResponse>,
    GroupHandler<ChatEntity>, RedisCacheHandler<ChatEntity> {

    private val identityService = IdentityService()

    override suspend fun handleCache(entity: ChatEntity) {
        val redis = DatabaseConnection.redis
        val chatKey = CacheKey.CHAT_KEY.format(entity.id.value)
        if (redis.exists(chatKey)) redis.del(chatKey)

        val admin = entity.admin
        if (admin != null) {
            val adminKey = CacheKey.IDENTITY_KEY.format(admin.id.value)
            if (redis.exists(adminKey)) redis.del(adminKey)
        }
        if (!entity.identities.empty()) {
            entity.identities.forEach { identityEntity ->
                val identityKey = CacheKey.IDENTITY_KEY.format(identityEntity.id.value)
                if (redis.exists(identityKey)) redis.del(identityKey)
            }
        }
        if (!entity.messages.empty()) {
            entity.messages.forEach { messageEntity ->
                val messageKey = CacheKey.MESSAGE_KEY.format(messageEntity.id.value)
                if (redis.exists(messageKey)) redis.del(messageKey)
            }
        }
    }

    override suspend fun findAll(): List<ChatShortResponse> = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        ChatEntity.all().with(ChatEntity::admin, ChatEntity::images).map { it.toShortResponse() }
    }

    override suspend fun findEntity(id: UUID): ChatEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        ChatEntity.findById(id)!!
            .load(ChatEntity::admin, ChatEntity::images, ChatEntity::identities, ChatEntity::messages)
    }

    override suspend fun findById(id: UUID): ChatFullResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        val redis = DatabaseConnection.redis
        val chatKey = CacheKey.CHAT_KEY.format(id)
        if (redis.exists(chatKey)) {
            Json.decodeFromString<ChatFullResponse>(redis.get(chatKey))
        } else {
            val chatFullResponse = findEntity(id).toFullResponse()
            redis.set(chatKey, Json.encodeToString(chatFullResponse))
            chatFullResponse
        }
    }

    override suspend fun create(request: ChatRequest): ChatFullResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val chatEntity = ChatEntity.new { this.insert(request) }
            .load(ChatEntity::admin, ChatEntity::images, ChatEntity::identities, ChatEntity::messages)
        handleCache(chatEntity)
        val chatFullResponse = chatEntity.toFullResponse()
        val chatKey = CacheKey.CHAT_KEY.format(chatFullResponse.id)
        DatabaseConnection.redis.set(chatKey, Json.encodeToString(chatFullResponse))
        chatFullResponse
    }

    override suspend fun update(request: ChatRequest): ChatFullResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val chatEntity = ChatEntity.findByIdAndUpdate(request.id!!) { it.update(request) }!!
            .load(ChatEntity::admin, ChatEntity::images, ChatEntity::identities, ChatEntity::messages)
        handleCache(chatEntity)
        chatEntity.toFullResponse()
    }

    override suspend fun delete(id: UUID) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val chatEntity = findEntity(id)
        handleCache(chatEntity)
        chatEntity.delete()
    }

    override suspend fun join(groupRequest: GroupRequest): ChatEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val chatEntity = findEntity(groupRequest.groupId)
        val identityEntity = identityService.findEntity(groupRequest.applicantId)
        if (!chatEntity.identities.contains(identityEntity)) {
            chatEntity.identities = SizedCollection(chatEntity.identities + identityEntity)
            handleCache(chatEntity)
            identityService.handleCache(identityEntity)
            chatEntity
        } else {
            throw IllegalArgumentException("Applicant already in chat")
        }
    }

    override suspend fun out(groupRequest: GroupRequest): ChatEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val chatEntity = findEntity(groupRequest.groupId)
        val identityEntity = identityService.findEntity(groupRequest.applicantId)
        if (chatEntity.identities.map { it.id.value }.contains(identityEntity.id.value)) {
            IdentityChatTable.deleteWhere {
                (IdentityChatTable.chatId eq chatEntity.id.value) and
                        (IdentityChatTable.identityId eq identityEntity.id.value) }
            handleCache(chatEntity)
            identityService.handleCache(identityEntity)
            chatEntity
        } else {
            throw IllegalArgumentException("Applicant not in chat")
        }
    }
}