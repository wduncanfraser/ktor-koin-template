package com.example.todolist.api.mappers

import com.example.core.api.exceptions.ProblemDetailsException
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.runCatching
import io.ktor.http.HttpStatusCode
import java.util.UUID

object TodoListIdMapper {
    fun toDomain(listId: String): UUID = runCatching { UUID.fromString(listId) }
        .getOrThrow {
            ProblemDetailsException(
                type = "https://example.com/errors/not-found",
                statusCode = HttpStatusCode.NotFound,
                message = "Todo list not found: listId=$listId",
                cause = null,
            )
        }
}
