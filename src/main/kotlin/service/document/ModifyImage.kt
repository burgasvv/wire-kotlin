package org.burgas.service.document

import org.burgas.dao.Image
import java.util.UUID

interface ModifyImage<out I : Image> {

    suspend fun makePreview(entityId: UUID, imageId: UUID): I
}