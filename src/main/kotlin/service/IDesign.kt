package org.burgas.service

import org.burgas.dto.Request
import org.burgas.dto.Response

interface IDesign<ID, in R : Request, out F : Response> {

    fun create(request: R): F

    fun delete(id: ID)
}