package org.burgas.service

import kotlinx.coroutines.Dispatchers
import org.burgas.dao.CommentFileEntity
import org.burgas.database.DatabaseConnection
import org.burgas.service.document.ReadDocument
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

class CommentFileService : ReadDocument<UUID, CommentFileEntity> {

    override suspend fun findEntity(id: UUID): CommentFileEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        CommentFileEntity.findById(id)!!.load(CommentFileEntity::comment)
    }
}