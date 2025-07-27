package com.example.todo.api.mappers

import com.example.core.api.exceptions.ProblemDetailsException
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.runCatching
import io.ktor.http.HttpStatusCode
import java.util.UUID

object TodoIdMapper {
    fun toDomain(todoId: String): UUID = runCatching { UUID.fromString(todoId) }
        .getOrThrow {
            ProblemDetailsException(
                type = "https://example.com/errors/not-found",
                statusCode = HttpStatusCode.NotFound,
                message = "Todo not found: todoId=$todoId",
                cause = null,
            )
        }
}
