package org.burgas.cache

import org.burgas.dao.Dao

interface RedisCacheHandler<in D : Dao> {

    suspend fun handleCache(entity: D)
}