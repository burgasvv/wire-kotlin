package org.burgas.service

import kotlinx.coroutines.Dispatchers
import org.burgas.dao.PublicationImageEntity
import org.burgas.database.DatabaseConnection
import org.burgas.service.document.ReadDocument
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

class PublicationImageService : ReadDocument<UUID, PublicationImageEntity> {

    override suspend fun findEntity(id: UUID): PublicationImageEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        PublicationImageEntity.findById(id)!!.load(PublicationImageEntity::publication)
    }
}