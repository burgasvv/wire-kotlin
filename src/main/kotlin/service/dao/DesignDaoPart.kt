package org.burgas.service.dao

import io.ktor.http.content.*
import org.burgas.dto.Request
import org.burgas.dto.Response

interface DesignDaoPart<ID, in R : Request, out F : Response> {

    suspend fun create(request: R, files: List<PartData>): F

    suspend fun delete(id: ID)
}