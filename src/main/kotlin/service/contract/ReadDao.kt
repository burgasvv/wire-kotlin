package org.burgas.service.contract

import org.burgas.dto.Response

interface ReadDao<in ID, out F : Response> {

    suspend fun findById(id: ID): F
}