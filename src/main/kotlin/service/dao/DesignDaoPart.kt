package org.burgas.service.dao

import io.ktor.http.content.MultiPartData
import org.burgas.dto.Response

interface DesignDaoPart<ID, out F : Response> {

    suspend fun create(multiPartData: MultiPartData): F

    suspend fun delete(id: ID)
}