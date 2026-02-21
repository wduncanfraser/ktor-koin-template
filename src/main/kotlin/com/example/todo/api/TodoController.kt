package com.example.todo.api

import com.example.core.api.exceptions.ProblemDetailsException
import com.example.todo.api.mappers.TodoContractMapper
import com.example.todo.api.mappers.TodoIdMapper
import com.example.todo.services.TodoService
import com.example.todo.services.TodoServiceError
import com.example.generated.api.controllers.TodosController
import com.example.generated.api.controllers.TypedApplicationCall
import com.example.generated.api.models.CreateTodoRequestContract
import com.example.generated.api.models.ListTodosResponseContract
import com.example.generated.api.models.TodoResponseContract
import com.example.generated.api.models.UpdateTodoRequestContract
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.map
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

class TodoController(
    private val todoService: TodoService,
): TodosController {
    override suspend fun listTodos(
        pageSize: Int?,
        page: Int?,
        completed: Boolean?,
        call: TypedApplicationCall<ListTodosResponseContract>
    ) {
        val pageSize = pageSize ?: DEFAULT_PAGE_SIZE
        val page = page ?: 1
        val result = todoService.listTodos(pageSize, page, completed)
            .getOrThrow(::mapErrorToException)
        call.respondTyped(TodoContractMapper.toContract(result))
    }

    override suspend fun getTodo(
        todoId: String,
        call: TypedApplicationCall<TodoResponseContract>,
    ) {
        val response = todoService.getTodo(TodoIdMapper.toDomain(todoId))
            .map(TodoContractMapper::toContract)
            .getOrThrow(::mapErrorToException)
        call.respondTyped(response)
    }

    override suspend fun createTodo(
        createTodoRequest: CreateTodoRequestContract,
        call: TypedApplicationCall<TodoResponseContract>
    ) {
        val response = todoService.createTodo(TodoContractMapper.toDomain(createTodoRequest))
            .map(TodoContractMapper::toContract)
            .getOrThrow(::mapErrorToException)

        call.respondTyped(response)
    }

    override suspend fun updateTodo(
        todoId: String,
        updateTodoRequest: UpdateTodoRequestContract,
        call: TypedApplicationCall<TodoResponseContract>
    ) {
        val response = todoService.updateTodo(TodoContractMapper.toDomain(todoId, updateTodoRequest))
            .map(TodoContractMapper::toContract)
            .getOrThrow(::mapErrorToException)

        call.respondTyped(response)
    }

    override suspend fun deleteTodo(todoId: String, call: ApplicationCall) {
        todoService.deleteTodo(
            TodoIdMapper.toDomain(todoId)
        ).getOrThrow(::mapErrorToException)

        call.respond(HttpStatusCode.NoContent)
    }

    companion object {
        private const val DEFAULT_PAGE_SIZE = 20

        private fun mapErrorToException(error: TodoServiceError): Throwable {
            return when (error) {
                is TodoServiceError.TodoNotFound -> ProblemDetailsException(
                    type = "https://example.com/errors/not-found",
                    statusCode = HttpStatusCode.NotFound,
                    message = "Todo not found: todoId=${error.id}",
                    cause = null,
                )

                is TodoServiceError.UnhandledServiceError -> RuntimeException(
                    "Unexpected exception",
                    error.t,
                )
            }
        }
    }
}
