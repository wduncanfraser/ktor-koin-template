package com.example.core.api.exceptions

import io.ktor.http.HttpStatusCode

/**
 * Standard exception class for our controllers to throw, mapping to an HTTP status code and standard error response
 * body.
 */
data class ProblemDetailsException(
    val type: String,
    val statusCode: HttpStatusCode,
    override val message: String,
    override val cause: Throwable?
) : RuntimeException()
