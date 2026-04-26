package com.example.todolist.api

import com.example.authn.UserSession
import com.example.core.api.exceptions.ProblemDetailsException
import com.example.generated.api.controllers.TodoListsController
import com.example.generated.api.controllers.TodoListsTodosController
import com.example.generated.api.controllers.TypedApplicationCall
import com.example.generated.api.models.CreateTodoListRequestContract
import com.example.generated.api.models.CreateTodoRequestContract
import com.example.generated.api.models.ListTodoListsResponseContract
import com.example.generated.api.models.ListTodosResponseContract
import com.example.generated.api.models.TodoListResponseContract
import com.example.generated.api.models.TodoResponseContract
import com.example.generated.api.models.UpdateTodoListRequestContract
import com.example.generated.api.models.UpdateTodoRequestContract
import com.example.todo.services.TodoService
import com.example.todo.services.TodoServiceError
import com.example.todolist.api.mappers.TodoContractMapper
import com.example.todolist.api.mappers.TodoListContractMapper
import com.example.todolist.api.mappers.TodoListIdMapper
import com.example.todolist.api.mappers.TodoIdMapper
import com.example.todolist.services.TodoListService
import com.example.todolist.services.TodoListServiceError
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.map
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.response.respond

class TodoListController(
    private val todoListService: TodoListService,
    private val todoService: TodoService,
) : TodoListsController, TodoListsTodosController {

    override suspend fun listTodoLists(
        pageSize: Int?,
        page: Int?,
        call: TypedApplicationCall<ListTodoListsResponseContract>,
    ) {
        val userId = call.principal<UserSession>()!!.userId
        val result = todoListService.listTodoLists(userId, pageSize ?: DEFAULT_PAGE_SIZE, page ?: 1)
            .map(TodoListContractMapper::toContract)
            .getOrThrow(::mapListErrorToException)
        call.respondTyped(result)
    }

    override suspend fun createTodoList(
        createTodoListRequest: CreateTodoListRequestContract,
        call: TypedApplicationCall<TodoListResponseContract>,
    ) {
        val userId = call.principal<UserSession>()!!.userId
        val result = todoListService.createTodoList(userId, TodoListContractMapper.toDomain(createTodoListRequest))
            .map(TodoListContractMapper::toContract)
            .getOrThrow(::mapListErrorToException)
        call.respond(HttpStatusCode.Created, result)
    }

    override suspend fun getTodoList(
        listId: String,
        call: TypedApplicationCall<TodoListResponseContract>,
    ) {
        val userId = call.principal<UserSession>()!!.userId
        val result = todoListService.getTodoList(userId, TodoListIdMapper.toDomain(listId))
            .map(TodoListContractMapper::toContract)
            .getOrThrow(::mapListErrorToException)
        call.respondTyped(result)
    }

    override suspend fun updateTodoList(
        listId: String,
        updateTodoListRequest: UpdateTodoListRequestContract,
        call: TypedApplicationCall<TodoListResponseContract>,
    ) {
        val userId = call.principal<UserSession>()!!.userId
        val result = todoListService.updateTodoList(
            userId,
            TodoListContractMapper.toDomain(listId, updateTodoListRequest),
        )
            .map(TodoListContractMapper::toContract)
            .getOrThrow(::mapListErrorToException)
        call.respondTyped(result)
    }

    override suspend fun deleteTodoList(listId: String, call: ApplicationCall) {
        val userId = call.principal<UserSession>()!!.userId
        todoListService.deleteTodoList(userId, TodoListIdMapper.toDomain(listId))
            .getOrThrow(::mapListErrorToException)
        call.respond(HttpStatusCode.NoContent)
    }

    override suspend fun listTodosInList(
        listId: String,
        pageSize: Int?,
        page: Int?,
        completed: Boolean?,
        call: TypedApplicationCall<ListTodosResponseContract>,
    ) {
        val userId = call.principal<UserSession>()!!.userId
        val listUuid = TodoListIdMapper.toDomain(listId)
        todoListService.getTodoList(userId, listUuid).getOrThrow(::mapListErrorToException)
        val result = todoService.listTodos(listUuid, pageSize ?: DEFAULT_PAGE_SIZE, page ?: 1, completed)
            .map(TodoContractMapper::toContract)
            .getOrThrow(::mapTodoErrorToException)
        call.respondTyped(result)
    }

    override suspend fun createTodoInList(
        listId: String,
        createTodoRequest: CreateTodoRequestContract,
        call: TypedApplicationCall<TodoResponseContract>,
    ) {
        val userId = call.principal<UserSession>()!!.userId
        val listUuid = TodoListIdMapper.toDomain(listId)
        todoListService.getTodoList(userId, listUuid).getOrThrow(::mapListErrorToException)
        val result = todoService.createTodo(listUuid, userId, TodoContractMapper.toDomain(createTodoRequest))
            .map(TodoContractMapper::toContract)
            .getOrThrow(::mapTodoErrorToException)
        call.respond(HttpStatusCode.Created, result)
    }

    override suspend fun getTodoInList(
        listId: String,
        todoId: String,
        call: TypedApplicationCall<TodoResponseContract>,
    ) {
        val userId = call.principal<UserSession>()!!.userId
        val listUuid = TodoListIdMapper.toDomain(listId)
        todoListService.getTodoList(userId, listUuid).getOrThrow(::mapListErrorToException)
        val result = todoService.getTodo(listUuid, TodoIdMapper.toDomain(todoId))
            .map(TodoContractMapper::toContract)
            .getOrThrow(::mapTodoErrorToException)
        call.respondTyped(result)
    }

    override suspend fun updateTodoInList(
        listId: String,
        todoId: String,
        updateTodoRequest: UpdateTodoRequestContract,
        call: TypedApplicationCall<TodoResponseContract>,
    ) {
        val userId = call.principal<UserSession>()!!.userId
        val listUuid = TodoListIdMapper.toDomain(listId)
        todoListService.getTodoList(userId, listUuid).getOrThrow(::mapListErrorToException)
        val result = todoService.updateTodo(listUuid, TodoContractMapper.toDomain(todoId, updateTodoRequest))
            .map(TodoContractMapper::toContract)
            .getOrThrow(::mapTodoErrorToException)
        call.respondTyped(result)
    }

    override suspend fun deleteTodoInList(
        listId: String,
        todoId: String,
        call: ApplicationCall,
    ) {
        val userId = call.principal<UserSession>()!!.userId
        val listUuid = TodoListIdMapper.toDomain(listId)
        todoListService.getTodoList(userId, listUuid).getOrThrow(::mapListErrorToException)
        todoService.deleteTodo(listUuid, TodoIdMapper.toDomain(todoId))
            .getOrThrow(::mapTodoErrorToException)
        call.respond(HttpStatusCode.NoContent)
    }

    companion object {
        private const val DEFAULT_PAGE_SIZE = 20

        private fun mapListErrorToException(error: TodoListServiceError): Throwable = when (error) {
            is TodoListServiceError.TodoListNotFound -> ProblemDetailsException(
                type = "https://example.com/errors/not-found",
                statusCode = HttpStatusCode.NotFound,
                message = "Todo list not found: listId=${error.id}",
                cause = null,
            )

            is TodoListServiceError.ValidationFailed -> ProblemDetailsException(
                type = "https://example.com/errors/unprocessable-entity",
                statusCode = HttpStatusCode.UnprocessableEntity,
                message = "Request validation failed",
                cause = null,
                errors = error.errors
                    .groupBy { it.path.trimStart('.') }
                    .mapValues { (_, errs) -> errs.map { it.message } },
            )

            is TodoListServiceError.UnhandledServiceError -> RuntimeException(
                "Unexpected exception",
                error.t,
            )
        }

        private fun mapTodoErrorToException(error: TodoServiceError): Throwable = when (error) {
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
