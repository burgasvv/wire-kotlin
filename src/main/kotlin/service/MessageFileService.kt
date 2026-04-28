package org.burgas.service

import kotlinx.coroutines.Dispatchers
import org.burgas.dao.MessageFileEntity
import org.burgas.database.DatabaseConnection
import org.burgas.service.document.ReadDocument
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

class MessageFileService : ReadDocument<UUID, MessageFileEntity> {

    override suspend fun findEntity(id: UUID): MessageFileEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        MessageFileEntity.findById(id)!!.load(MessageFileEntity::message)
    }
}