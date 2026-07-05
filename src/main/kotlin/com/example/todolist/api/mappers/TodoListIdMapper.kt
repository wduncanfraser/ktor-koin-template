package com.example.todolist.api.mappers

import com.example.core.api.ProblemDetailsDefaults
import com.example.core.api.exceptions.ProblemDetailsException
import com.example.core.api.extensions.todoListNotFoundMessage
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.runCatching
import io.ktor.http.HttpStatusCode
import java.util.UUID

object TodoListIdMapper {
    fun toDomain(listId: String): UUID = runCatching { UUID.fromString(listId) }
        .getOrThrow {
            ProblemDetailsException(
                type = ProblemDetailsDefaults.NotFound.TYPE,
                statusCode = HttpStatusCode.NotFound,
                message = todoListNotFoundMessage(listId),
                cause = null,
            )
        }
}
