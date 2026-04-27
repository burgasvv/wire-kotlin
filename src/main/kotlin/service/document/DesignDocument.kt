package org.burgas.service.document

import io.ktor.http.content.*
import org.burgas.dto.DocumentRequest
import java.util.*

interface DesignDocument {

    suspend fun create(entityId: UUID, multiPartData: MultiPartData)

    suspend fun delete(entityId: UUID, documentRequest: DocumentRequest)
}