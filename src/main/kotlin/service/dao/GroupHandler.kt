package org.burgas.service.dao

import org.burgas.dao.Dao
import org.burgas.dto.GroupRequest

interface GroupHandler<out D : Dao> {

    suspend fun join(groupRequest: GroupRequest): D

    suspend fun out(groupRequest: GroupRequest): D
}