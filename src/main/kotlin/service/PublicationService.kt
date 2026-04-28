package org.burgas.service

import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.burgas.cache.CacheKey
import org.burgas.cache.RedisCacheHandler
import org.burgas.dao.PublicationEntity
import org.burgas.dao.PublicationFileEntity
import org.burgas.dao.PublicationImageEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.PublicationFullResponse
import org.burgas.service.dao.DesignDaoPart
import org.burgas.service.dao.ReadDao
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.UUID

class PublicationService : ReadDao<UUID, PublicationEntity, PublicationFullResponse>,
    DesignDaoPart<UUID, PublicationFullResponse>, RedisCacheHandler<PublicationEntity> {

    override suspend fun handleCache(entity: PublicationEntity) {
        val redis = DatabaseConnection.redis
        val publicationKey = CacheKey.PUBLICATION_KEY.format(entity.id.value)
        if (redis.exists(publicationKey)) redis.del(publicationKey)

        val community = entity.community
        val communityKey = CacheKey.COMMUNITY_KEY.format(community.id.value)
        if (redis.exists(communityKey)) redis.del(communityKey)

        val sender = entity.sender
        if (sender != null) {
            val senderKey = CacheKey.IDENTITY_KEY.format(sender.id.value)
            if (redis.exists(senderKey)) redis.del(senderKey)
        }
        val comments = entity.comments
        if (!comments.empty()) {
            comments.forEach { commentEntity ->
                val commentKey = CacheKey.COMMENT_KEY.format(commentEntity.id.value)
                if (redis.exists(commentKey)) redis.del(commentKey)
            }
        }
    }

    override suspend fun findEntity(id: UUID): PublicationEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        PublicationEntity.findById(id)!!
            .load(
                PublicationEntity::community, PublicationEntity::sender,
                PublicationEntity::images, PublicationEntity::files, PublicationEntity::comments
            )
    }

    override suspend fun findById(id: UUID): PublicationFullResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        val redis = DatabaseConnection.redis
        val publicationKey = CacheKey.PUBLICATION_KEY.format(id)
        if (redis.exists(publicationKey)) {
            Json.decodeFromString<PublicationFullResponse>(redis.get(publicationKey))
        } else {
            val publicationFullResponse = findEntity(id).toFullResponse()
            redis.set(publicationKey, Json.encodeToString(publicationFullResponse))
            publicationFullResponse
        }
    }

    override suspend fun create(multiPartData: MultiPartData): PublicationFullResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val readPart = multiPartData.readPart()!!
        val publicationEntity = PublicationEntity.new { this.insert(readPart) }
        handleCache(publicationEntity)
        multiPartData.forEachPart { partData ->
            if (partData is PartData.FileItem) {
                if (partData.contentType!!.contentType.startsWith("image")) {
                    PublicationImageEntity.new { this.upload(publicationEntity, partData) }
                } else {
                    PublicationFileEntity.new { this.upload(publicationEntity, partData) }
                }
            }
        }
        val publicationFullResponse = findEntity(publicationEntity.id.value).toFullResponse()
        val publicationKey = CacheKey.PUBLICATION_KEY.format(publicationFullResponse.id)
        DatabaseConnection.redis.set(publicationKey, Json.encodeToString(publicationFullResponse))
        publicationFullResponse
    }

    override suspend fun delete(id: UUID) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val publicationEntity = findEntity(id)
        handleCache(publicationEntity)
        publicationEntity.delete()
    }
}