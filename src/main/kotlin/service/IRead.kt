package org.burgas.service

import org.burgas.dao.Dao
import org.burgas.dto.Response

interface IRead<in ID, out E : Dao, out F : Response> {

    fun findEntity(id: ID): E

    fun findById(id: ID): F
}