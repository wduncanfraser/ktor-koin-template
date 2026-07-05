package com.example.core.api.extensions

import com.example.authn.UserSession
import com.example.core.api.ProblemDetailsDefaults
import com.example.core.api.exceptions.ProblemDetailsException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal

/**
 * Retrieve the [UserSession] principal. Throws Unauthorized if inaccessible.
 */
fun ApplicationCall.requirePrincipal(): UserSession =
    principal<UserSession>() ?: throw ProblemDetailsException(
        type = ProblemDetailsDefaults.Unauthorized.TYPE,
        statusCode = HttpStatusCode.Unauthorized,
        message = ProblemDetailsDefaults.Unauthorized.MESSAGE,
    )
