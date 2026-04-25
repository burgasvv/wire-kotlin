package org.burgas.service

import org.burgas.dto.Request
import org.burgas.dto.Response

interface IModify<in R : Request, out F : Response> {

    fun update(request: R): F
}