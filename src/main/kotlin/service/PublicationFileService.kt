package org.burgas.service

import kotlinx.coroutines.Dispatchers
import org.burgas.dao.PublicationFileEntity
import org.burgas.database.DatabaseConnection
import org.burgas.service.document.ReadDocument
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

class PublicationFileService : ReadDocument<UUID, PublicationFileEntity> {

    override suspend fun findEntity(id: UUID): PublicationFileEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        PublicationFileEntity.findById(id)!!.load(PublicationFileEntity::publication)
    }
}