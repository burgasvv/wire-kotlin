package org.burgas.service.dao

import org.burgas.dto.Response

interface ListDao<out S : Response> {

    suspend fun findAll(): List<S>
}