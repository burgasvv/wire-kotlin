package org.burgas.service

import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.burgas.cache.CacheKey
import org.burgas.cache.RedisCacheHandler
import org.burgas.dao.CommentEntity
import org.burgas.dao.CommentFileEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.CommentFullResponse
import org.burgas.service.dao.DesignDaoPart
import org.burgas.service.dao.ReadDao
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.UUID

class CommentService : ReadDao<UUID, CommentEntity, CommentFullResponse>,
    DesignDaoPart<UUID, CommentFullResponse>, RedisCacheHandler<CommentEntity> {

    override suspend fun handleCache(entity: CommentEntity) {
        val redis = DatabaseConnection.redis
        val commentKey = CacheKey.COMMENT_KEY.format(entity.id.value)
        if (redis.exists(commentKey)) redis.del(commentKey)

        val publication = entity.publication
        val publicationKey = CacheKey.PUBLICATION_KEY.format(publication.id.value)
        if (redis.exists(publicationKey)) redis.del(publicationKey)

        val sender = entity.sender
        if (sender != null) {
            val senderKey = CacheKey.IDENTITY_KEY.format(sender.id.value)
            if (redis.exists(senderKey)) redis.del(senderKey)
        }
    }

    override suspend fun findEntity(id: UUID): CommentEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        CommentEntity.findById(id)!!
            .load(CommentEntity::publication, CommentEntity::sender, CommentEntity::files)
    }

    override suspend fun findById(id: UUID): CommentFullResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        val redis = DatabaseConnection.redis
        val commentKey = CacheKey.COMMENT_KEY.format(id)
        if (redis.exists(commentKey)) {
            Json.decodeFromString<CommentFullResponse>(redis.get(commentKey))
        } else {
            val commentFullResponse = findEntity(id).toFullResponse()
            redis.set(commentKey, Json.encodeToString(commentFullResponse))
            commentFullResponse
        }
    }

    override suspend fun create(multiPartData: MultiPartData): CommentFullResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val readPart = multiPartData.readPart()!!
        val commentEntity = CommentEntity.new { this.insert(readPart) }
        handleCache(commentEntity)
        multiPartData.forEachPart { partData ->
            if (partData is PartData.FileItem) CommentFileEntity.new { this.upload(commentEntity, partData) }
        }
        val commentFullResponse = findEntity(commentEntity.id.value).toFullResponse()
        val commentKey = CacheKey.COMMENT_KEY.format(commentFullResponse.id)
        DatabaseConnection.redis.set(commentKey, Json.encodeToString(commentFullResponse))
        commentFullResponse
    }

    override suspend fun delete(id: UUID) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val commentEntity = findEntity(id)
        handleCache(commentEntity)
        commentEntity.delete()
    }
}