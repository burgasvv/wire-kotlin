package org.burgas.service.document

import org.burgas.dao.Image
import org.burgas.dto.ImageRequest
import java.util.UUID

interface ModifyImage<out I : Image> {

    suspend fun makePreview(imageRequest: ImageRequest): I
}