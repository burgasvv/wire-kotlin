package org.burgas.service

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.burgas.cache.CacheKey
import org.burgas.cache.RedisCacheHandler
import org.burgas.dao.IdentityEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.IdentityFullResponse
import org.burgas.dto.IdentityRequest
import org.burgas.dto.IdentityShortResponse
import org.burgas.service.dao.DesignDao
import org.burgas.service.dao.ListDao
import org.burgas.service.dao.ModifyDao
import org.burgas.service.dao.ReadDao
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.*

class IdentityService : ListDao<IdentityShortResponse>, ReadDao<UUID, IdentityEntity, IdentityFullResponse>,
    DesignDao<UUID, IdentityRequest, IdentityFullResponse>, ModifyDao<IdentityRequest, IdentityFullResponse>,
    RedisCacheHandler<IdentityEntity> {

    override suspend fun handleCache(entity: IdentityEntity) {
        val redis = DatabaseConnection.redis
        val identityKey = CacheKey.IDENTITY_KEY.format(entity.id.value)
        if (redis.exists(identityKey)) redis.del(identityKey)

        if (!entity.chats.empty()) {
            entity.chats.forEach { chatEntity ->
                val chatKey = CacheKey.CHAT_KEY.format(chatEntity.id.value)
                if (redis.exists(chatKey)) redis.del(chatKey)
            }
        }
        if (!entity.communities.empty()) {
            entity.communities.forEach { communityEntity ->
                val communityKey = CacheKey.COMMUNITY_KEY.format(communityEntity.id.value)
                if (redis.exists(communityKey)) redis.del(communityKey)
            }
        }
    }

    override suspend fun findEntity(id: UUID): IdentityEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        IdentityEntity.findById(id)!!
            .load(IdentityEntity::images, IdentityEntity::chats, IdentityEntity::communities)
    }

    override suspend fun findAll(): List<IdentityShortResponse> = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        IdentityEntity.all().with(IdentityEntity::images).map { it.toShortResponse() }
    }

    override suspend fun findById(id: UUID): IdentityFullResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        val redis = DatabaseConnection.redis
        val identityKey = CacheKey.IDENTITY_KEY.format(id)
        if (redis.exists(identityKey)) {
            Json.decodeFromString<IdentityFullResponse>(redis.get(identityKey))
        } else {
            val identityFullResponse = findEntity(id).toFullResponse()
            redis.set(identityKey, Json.encodeToString(identityFullResponse))
            identityFullResponse
        }
    }

    override suspend fun create(request: IdentityRequest): IdentityFullResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val redis = DatabaseConnection.redis
        val identityFullResponse = IdentityEntity.new { this.insert(request) }
            .load(IdentityEntity::images, IdentityEntity::chats, IdentityEntity::communities)
            .toFullResponse()
        val identityKey = CacheKey.IDENTITY_KEY.format(identityFullResponse.id)
        redis.set(identityKey, Json.encodeToString(identityFullResponse))
        identityFullResponse
    }

    override suspend fun update(request: IdentityRequest): IdentityFullResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val identityEntity = IdentityEntity.findByIdAndUpdate(request.id!!) { it.update(request) }!!
            .load(IdentityEntity::images, IdentityEntity::chats, IdentityEntity::communities)
        handleCache(identityEntity)
        identityEntity.toFullResponse()
    }

    override suspend fun delete(id: UUID) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val identityEntity = findEntity(id)
        handleCache(identityEntity)
        identityEntity.delete()
    }
}

