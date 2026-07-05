package com.example.todo.api.mappers

import com.example.core.api.ProblemDetailsDefaults
import com.example.core.api.exceptions.ProblemDetailsException
import com.example.core.api.extensions.todoNotFoundMessage
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.runCatching
import io.ktor.http.HttpStatusCode
import java.util.UUID

object TodoIdMapper {
    fun toDomain(todoId: String): UUID = runCatching { UUID.fromString(todoId) }
        .getOrThrow {
            ProblemDetailsException(
                type = ProblemDetailsDefaults.NotFound.TYPE,
                statusCode = HttpStatusCode.NotFound,
                message = todoNotFoundMessage(todoId),
                cause = null,
            )
        }
}
