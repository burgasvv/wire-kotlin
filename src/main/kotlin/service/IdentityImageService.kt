package org.burgas.service

import io.ktor.http.content.*
import kotlinx.coroutines.Dispatchers
import org.burgas.dao.IdentityImageEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.DocumentRequest
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
        identityService.handleCache(identityEntity)
        multiPartData.forEachPart { partData ->
            IdentityImageEntity.new { this.upload(identityEntity, partData) }
        }
    }

    override suspend fun delete(entityId: UUID, documentRequest: DocumentRequest) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val identityEntity = identityService.findEntity(entityId)
        identityService.handleCache(identityEntity)
        if (!identityEntity.images.empty()) {
            identityEntity.images.forEach { identityImageEntity ->
                if (documentRequest.documentIds.contains(identityImageEntity.id.value)) identityImageEntity.delete()
            }
        } else {
            throw IllegalArgumentException("Identity images is empty")
        }
    }

    override suspend fun makePreview(entityId: UUID, imageId: UUID): IdentityImageEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val identityEntity = identityService.findEntity(entityId)
        identityService.handleCache(identityEntity)
        val images = identityEntity.images
        if (!images.empty()) {
            images.filter { it.preview }.forEach { it.preview = false }
            val identityImageEntity = images.find { it.id.value == imageId }
            if (identityImageEntity != null) {
                identityImageEntity.preview = true
                identityImageEntity
            } else {
                throw IllegalArgumentException("Image not belong to identity")
            }
        } else {
            throw IllegalArgumentException("Identity images is empty")
        }
    }
}