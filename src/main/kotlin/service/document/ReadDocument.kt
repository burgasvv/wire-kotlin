package org.burgas.service.document

import org.burgas.dao.Document

interface ReadDocument<in ID, out D : Document> {

    suspend fun findEntity(id: ID): D
}