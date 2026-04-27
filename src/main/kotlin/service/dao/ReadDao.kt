package org.burgas.service.dao

import org.burgas.dao.Dao
import org.burgas.dto.Response

interface ReadDao<in ID, out D : Dao, out F : Response> {

    suspend fun findEntity(id: ID): D

    suspend fun findById(id: ID): F
}