package org.burgas.service

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.burgas.cache.CacheKey
import org.burgas.cache.RedisCacheHandler
import org.burgas.dao.CommunityEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.CommunityFullResponse
import org.burgas.dto.CommunityRequest
import org.burgas.dto.CommunityShortResponse
import org.burgas.dto.GroupRequest
import org.burgas.service.dao.DesignDao
import org.burgas.service.dao.GroupHandler
import org.burgas.service.dao.ListDao
import org.burgas.service.dao.ModifyDao
import org.burgas.service.dao.ReadDao
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.*

class CommunityService : ListDao<CommunityShortResponse>, ReadDao<UUID, CommunityEntity, CommunityFullResponse>,
    DesignDao<UUID, CommunityRequest, CommunityFullResponse>, ModifyDao<CommunityRequest, CommunityFullResponse>,
    GroupHandler<CommunityEntity>, RedisCacheHandler<CommunityEntity> {

    private val identityService = IdentityService()

    override suspend fun handleCache(entity: CommunityEntity) {
        val redis = DatabaseConnection.redis
        val communityKey = CacheKey.COMMUNITY_KEY.format(entity.id.value)
        if (redis.exists(communityKey)) redis.del(communityKey)

        val admin = entity.admin
        if (admin != null) {
            val adminKey = CacheKey.IDENTITY_KEY.format(admin.id.value)
            if (redis.exists(adminKey)) redis.del(adminKey)
        }
        val identities = entity.identities
        if (!identities.empty()) {
            identities.forEach { identityEntity ->
                val identityKey = CacheKey.IDENTITY_KEY.format(identityEntity.id.value)
                if (redis.exists(identityKey)) redis.del(identityKey)
            }
        }
        val publications = entity.publications
        if (!publications.empty()) {
            publications.forEach { publicationEntity ->
                val publicationKey = CacheKey.PUBLICATION_KEY.format(publicationEntity.id.value)
                if (redis.exists(publicationKey)) redis.del(publicationKey)
            }
        }
    }

    override suspend fun findAll(): List<CommunityShortResponse> = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        CommunityEntity.all().with(CommunityEntity::admin, CommunityEntity::images)
            .map { it.toShortResponse() }
    }

    override suspend fun findEntity(id: UUID): CommunityEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        CommunityEntity.findById(id)!!
            .load(
                CommunityEntity::admin,
                CommunityEntity::images,
                CommunityEntity::identities,
                CommunityEntity::publications
            )
    }

    override suspend fun findById(id: UUID): CommunityFullResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        val redis = DatabaseConnection.redis
        val communityKey = CacheKey.COMMUNITY_KEY.format(id)
        if (redis.exists(communityKey)) {
            Json.decodeFromString<CommunityFullResponse>(redis.get(communityKey))
        } else {
            val communityFullResponse = findEntity(id).toFullResponse()
            redis.set(communityKey, Json.encodeToString(communityFullResponse))
            communityFullResponse
        }
    }

    override suspend fun create(request: CommunityRequest): CommunityFullResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val communityEntity = CommunityEntity.new { this.insert(request) }
            .load(
                CommunityEntity::admin,
                CommunityEntity::images,
                CommunityEntity::identities,
                CommunityEntity::publications
            )
        handleCache(communityEntity)
        val communityFullResponse = communityEntity.toFullResponse()
        val communityKey = CacheKey.COMMUNITY_KEY.format(communityFullResponse.id)
        DatabaseConnection.redis.set(communityKey, Json.encodeToString(communityFullResponse))
        communityFullResponse
    }

    override suspend fun update(request: CommunityRequest): CommunityFullResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val communityEntity = CommunityEntity.findByIdAndUpdate(request.id!!) { it.update(request) }!!
            .load(
                CommunityEntity::admin,
                CommunityEntity::images,
                CommunityEntity::identities,
                CommunityEntity::publications
            )
        handleCache(communityEntity)
        communityEntity.toFullResponse()
    }

    override suspend fun delete(id: UUID) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val communityEntity = findEntity(id)
        handleCache(communityEntity)
        communityEntity.delete()
    }

    override suspend fun join(groupRequest: GroupRequest): CommunityEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val communityEntity = findEntity(groupRequest.groupId)
        handleCache(communityEntity)
        val identityEntity = identityService.findEntity(groupRequest.applicantId)
        identityService.handleCache(identityEntity)
        if (!communityEntity.identities.contains(identityEntity)) {
            communityEntity.identities = SizedCollection(communityEntity.identities + identityEntity)
            communityEntity
        } else {
            throw IllegalArgumentException("Applicant already in community")
        }
    }

    override suspend fun out(groupRequest: GroupRequest): CommunityEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val communityEntity = findEntity(groupRequest.groupId)
        handleCache(communityEntity)
        val identityEntity = identityService.findEntity(groupRequest.applicantId)
        identityService.handleCache(identityEntity)
        if (communityEntity.identities.contains(identityEntity)) {
            communityEntity.identities = SizedCollection(communityEntity.identities - identityEntity)
            communityEntity
        } else {
            throw IllegalArgumentException("Applicant not in community")
        }
    }
}