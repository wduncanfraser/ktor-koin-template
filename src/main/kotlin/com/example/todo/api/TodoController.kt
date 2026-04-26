package com.example.todo.api

import com.example.authn.UserSession
import com.example.core.api.exceptions.ProblemDetailsException
import com.example.generated.api.controllers.TodosController
import com.example.generated.api.controllers.TypedApplicationCall
import com.example.generated.api.models.ListTodosResponseContract
import com.example.todo.api.mappers.TodoContractMapper
import com.example.todo.services.TodoService
import com.example.todo.services.TodoServiceError
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.map
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal

class TodoController(
    private val todoService: TodoService,
) : TodosController {

    override suspend fun listTodos(
        pageSize: Int?,
        page: Int?,
        completed: Boolean?,
        call: TypedApplicationCall<ListTodosResponseContract>,
    ) {
        val userId = call.principal<UserSession>()!!.userId
        val result = todoService.listAllTodos(userId, pageSize ?: DEFAULT_PAGE_SIZE, page ?: 1, completed)
            .map(TodoContractMapper::toContract)
            .getOrThrow(::mapErrorToException)
        call.respondTyped(result)
    }

    companion object {
        private const val DEFAULT_PAGE_SIZE = 20

        private fun mapErrorToException(error: TodoServiceError): Throwable = when (error) {
            is TodoServiceError.TodoNotFound -> ProblemDetailsException(
                type = "https://example.com/errors/not-found",
                statusCode = HttpStatusCode.NotFound,
                message = "Todo not found: todoId=${error.id}",
                cause = null,
            )

            is TodoServiceError.ValidationFailed -> ProblemDetailsException(
                type = "https://example.com/errors/unprocessable-entity",
                statusCode = HttpStatusCode.UnprocessableEntity,
                message = "Request validation failed",
                cause = null,
                errors = error.errors
                    .groupBy { it.path.trimStart('.') }
                    .mapValues { (_, errs) -> errs.map { it.message } },
            )

            is TodoServiceError.UnhandledServiceError -> RuntimeException(
                "Unexpected exception",
                error.t,
            )
        }
    }
}
