package com.example.todo.api

import com.example.core.api.ProblemDetailsDefaults
import com.example.core.api.exceptions.ProblemDetailsException
import com.example.core.api.extensions.requirePrincipal
import com.example.core.api.extensions.toForbiddenMessage
import com.example.core.api.extensions.todoListNotFoundMessage
import com.example.core.api.extensions.todoNotFoundMessage
import com.example.generated.api.controllers.TodosController
import com.example.generated.api.controllers.TypedApplicationCall
import com.example.generated.api.models.ListTodosResponseContract
import com.example.todo.api.mappers.TodoContractMapper
import com.example.todo.services.TodoService
import com.example.todo.services.TodoServiceError
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.map
import io.ktor.http.HttpStatusCode

class TodoController(
    private val todoService: TodoService,
) : TodosController {

    override suspend fun listTodos(
        pageSize: Int?,
        page: Int?,
        completed: Boolean?,
        call: TypedApplicationCall<ListTodosResponseContract>,
    ) {
        val userId = call.requirePrincipal().userId
        val result = todoService.listAllTodos(userId, pageSize ?: DEFAULT_PAGE_SIZE, page ?: 1, completed)
            .map(TodoContractMapper::toContract)
            .getOrThrow(::mapErrorToException)
        call.respondTyped(result)
    }

    companion object {
        private const val DEFAULT_PAGE_SIZE = 20

        // internal (not private): TodoListController's todo sub-resource endpoints reuse this
        // directly rather than keeping a second, identical copy — both map the exact same
        // TodoServiceError shape. If TodoListController's needs ever diverge, split it back into
        // its own private function there instead of parameterizing this one.
        internal fun mapErrorToException(error: TodoServiceError): Throwable = when (error) {
            is TodoServiceError.TodoNotFound -> ProblemDetailsException(
                type = ProblemDetailsDefaults.NotFound.TYPE,
                statusCode = HttpStatusCode.NotFound,
                message = todoNotFoundMessage(error.id),
                cause = null,
            )

            is TodoServiceError.TodoListNotFound -> ProblemDetailsException(
                type = ProblemDetailsDefaults.NotFound.TYPE,
                statusCode = HttpStatusCode.NotFound,
                message = todoListNotFoundMessage(error.id),
                cause = null,
            )

            is TodoServiceError.ValidationFailed -> ProblemDetailsException(
                type = ProblemDetailsDefaults.ValidationFailed.TYPE,
                statusCode = HttpStatusCode.UnprocessableEntity,
                message = ProblemDetailsDefaults.ValidationFailed.MESSAGE,
                cause = null,
                errors = error.errors
                    .groupBy { it.path.trimStart('.') }
                    .mapValues { (_, errs) -> errs.map { it.message } },
            )

            is TodoServiceError.Forbidden -> ProblemDetailsException(
                type = ProblemDetailsDefaults.Forbidden.TYPE,
                statusCode = HttpStatusCode.Forbidden,
                message = error.resource.toForbiddenMessage(error.permission),
                cause = null,
            )

            is TodoServiceError.UnhandledServiceError -> RuntimeException(
                "Unexpected exception",
                error.t,
            )
        }
    }
}
