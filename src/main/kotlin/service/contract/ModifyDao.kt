package org.burgas.service.contract

import org.burgas.dto.Request
import org.burgas.dto.Response

interface ModifyDao<in R : Request, out F : Response> {

    suspend fun update(request: R): F
}