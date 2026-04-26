package org.burgas.service.contract

import org.burgas.dto.Response

interface IList<out S : Response> {

    suspend fun findAll(): List<S>
}