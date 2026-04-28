package org.burgas.service

import io.ktor.http.content.*
import kotlinx.coroutines.Dispatchers
import org.burgas.dao.IdentityImageEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.DocumentRequest
import org.burgas.dto.ImageRequest
import org.burgas.service.document.DesignDocument
import org.burgas.service.document.ModifyImage
import org.burgas.service.document.ReadDocument
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.*

class IdentityImageService : ReadDocument<UUID, IdentityImageEntity>, DesignDocument, ModifyImage<IdentityImageEntity> {

    private val identityService = IdentityService()

    override suspend fun findEntity(id: UUID): IdentityImageEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        IdentityImageEntity.findById(id)!!.load(IdentityImageEntity::identity)
    }

    override suspend fun create(entityId: UUID, multiPartData: MultiPartData) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val identityEntity = identityService.findEntity(entityId)
        multiPartData.forEachPart { partData ->
            IdentityImageEntity.new { this.upload(identityEntity, partData) }
        }
        identityService.handleCache(identityEntity)
    }

    override suspend fun delete(entityId: UUID, documentRequest: DocumentRequest) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val identityEntity = identityService.findEntity(entityId)
        val images = identityEntity.images
        if (!images.empty()) {
            images.forEach { identityImageEntity ->
                if (documentRequest.documentIds.contains(identityImageEntity.id.value)) identityImageEntity.delete()
            }
            identityService.handleCache(identityEntity)
        } else {
            throw IllegalArgumentException("Identity images empty")
        }
    }

    override suspend fun makePreview(imageRequest: ImageRequest): IdentityImageEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val identityEntity = identityService.findEntity(imageRequest.entityId)
        val images = identityEntity.images
        if (!images.empty()) {
            images.filter { it.preview }.forEach { it.preview = false }
            val identityImageEntity = images.find { it.id.value == imageRequest.imageId }
            if (identityImageEntity != null) {
                identityImageEntity.preview = true
                identityService.handleCache(identityEntity)
                identityImageEntity
            } else {
                throw IllegalArgumentException("Image not belong to identity")
            }
        } else {
            throw IllegalArgumentException("Identity images is empty")
        }
    }
}