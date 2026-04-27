package org.burgas.service

import io.ktor.http.content.*
import kotlinx.coroutines.Dispatchers
import org.burgas.dao.CommunityImageEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.DocumentRequest
import org.burgas.service.document.DesignDocument
import org.burgas.service.document.ModifyImage
import org.burgas.service.document.ReadDocument
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.*

class CommunityImageService : ReadDocument<UUID, CommunityImageEntity>,
    DesignDocument, ModifyImage<CommunityImageEntity> {

    private val communityService = CommunityService()

    override suspend fun findEntity(id: UUID): CommunityImageEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        CommunityImageEntity.findById(id)!!.load(CommunityImageEntity::community)
    }

    override suspend fun create(entityId: UUID, multiPartData: MultiPartData) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val communityEntity = communityService.findEntity(entityId)
        communityService.handleCache(communityEntity)
        multiPartData.forEachPart { partData ->
            CommunityImageEntity.new { this.upload(communityEntity, partData) }
        }
    }

    override suspend fun delete(entityId: UUID, documentRequest: DocumentRequest) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val communityEntity = communityService.findEntity(entityId)
        communityService.handleCache(communityEntity)
        val images = communityEntity.images
        if (!images.empty()) {
            images.forEach { communityImageEntity ->
                if (documentRequest.documentIds.contains(communityImageEntity.id.value)) communityImageEntity.delete()
            }
        } else {
            throw IllegalArgumentException("Community images empty")
        }
    }

    override suspend fun makePreview(entityId: UUID, imageId: UUID): CommunityImageEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val communityEntity = communityService.findEntity(entityId)
        communityService.handleCache(communityEntity)
        val images = communityEntity.images
        if (!images.empty()) {
            images.filter { it.preview }.forEach { it.preview = false }
            val communityImageEntity = images.find { it.id.value == imageId }
            if (communityImageEntity != null) {
                communityImageEntity.preview = true
                communityImageEntity
            } else {
                throw IllegalArgumentException("Image not belong to identity")
            }
        } else {
            throw IllegalArgumentException("Identity images is empty")
        }
    }
}