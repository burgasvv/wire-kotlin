package org.burgas.service

import io.ktor.http.content.MultiPartData
import io.ktor.http.content.forEachPart
import kotlinx.coroutines.Dispatchers
import org.burgas.dao.ChatImageEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.DocumentRequest
import org.burgas.dto.ImageRequest
import org.burgas.service.document.DesignDocument
import org.burgas.service.document.ModifyImage
import org.burgas.service.document.ReadDocument
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.UUID

class ChatImageService : ReadDocument<UUID, ChatImageEntity>, DesignDocument, ModifyImage<ChatImageEntity> {

    private val chatService = ChatService()

    override suspend fun findEntity(id: UUID): ChatImageEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        ChatImageEntity.findById(id)!!.load(ChatImageEntity::chat)
    }

    override suspend fun create(entityId: UUID, multiPartData: MultiPartData) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val chatEntity = chatService.findEntity(entityId)
        chatService.handleCache(chatEntity)
        multiPartData.forEachPart { partData ->
            ChatImageEntity.new { this.upload(chatEntity, partData) }
        }
    }

    override suspend fun delete(entityId: UUID, documentRequest: DocumentRequest) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val chatEntity = chatService.findEntity(entityId)
        chatService.handleCache(chatEntity)
        val images = chatEntity.images
        if (!images.empty()) {
            images.forEach { chatImageEntity ->
                if (documentRequest.documentIds.contains(chatImageEntity.id.value)) chatImageEntity.delete()
            }
        } else {
            throw IllegalArgumentException("Chat images empty")
        }
    }

    override suspend fun makePreview(imageRequest: ImageRequest): ChatImageEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val chatEntity = chatService.findEntity(imageRequest.entityId)
        chatService.handleCache(chatEntity)
        val images = chatEntity.images
        if (!images.empty()) {
            images.filter { it.preview }.forEach { it.preview = false }
            val chatImageEntity = images.find { it.id.value == imageRequest.imageId }
            if (chatImageEntity != null) {
                chatImageEntity.preview = true
                chatImageEntity
            } else {
                throw IllegalArgumentException("Image not belong to identity")
            }
        } else {
            throw IllegalArgumentException("Identity images is empty")
        }
    }
}