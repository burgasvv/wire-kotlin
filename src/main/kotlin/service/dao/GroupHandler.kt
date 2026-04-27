package org.burgas.service.dao

import org.burgas.dto.GroupRequest

interface GroupHandler {

    suspend fun join(groupRequest: GroupRequest)

    suspend fun out(groupRequest: GroupRequest)
}