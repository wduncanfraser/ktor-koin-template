package com.example.todolist.api

import com.example.core.api.ProblemDetailsDefaults
import com.example.core.api.exceptions.ProblemDetailsException
import com.example.core.api.extensions.requirePrincipal
import com.example.core.api.extensions.toForbiddenMessage
import com.example.core.api.extensions.todoListNotFoundMessage
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
import com.example.todo.api.TodoController
import com.example.todo.services.TodoService
import com.example.todo.api.mappers.TodoContractMapper
import com.example.todo.api.mappers.TodoIdMapper
import com.example.todolist.api.mappers.TodoListContractMapper
import com.example.todolist.api.mappers.TodoListIdMapper
import com.example.todolist.services.TodoListService
import com.example.todolist.services.TodoListServiceError
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.map
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

/**
 * Todo-list endpoints call [todoListService] directly. Todo sub-resource endpoints
 * (`*TodoInList`/`*TodosInList`) are pure pass-throughs to [todoService] with no authorization
 * pre-check here — [com.example.todo.services.TodoService] performs its own checks per operation.
 */
class TodoListController(
    private val todoListService: TodoListService,
    private val todoService: TodoService,
) : TodoListsController, TodoListsTodosController {

    override suspend fun listTodoLists(
        pageSize: Int?,
        page: Int?,
        call: TypedApplicationCall<ListTodoListsResponseContract>,
    ) {
        val userId = call.requirePrincipal().userId
        val result = todoListService.listTodoLists(userId, pageSize ?: DEFAULT_PAGE_SIZE, page ?: 1)
            .map(TodoListContractMapper::toContract)
            .getOrThrow(::mapListErrorToException)
        call.respondTyped(result)
    }

    override suspend fun createTodoList(
        createTodoListRequest: CreateTodoListRequestContract,
        call: TypedApplicationCall<TodoListResponseContract>,
    ) {
        val userId = call.requirePrincipal().userId
        val result = todoListService.createTodoList(userId, TodoListContractMapper.toDomain(createTodoListRequest))
            .map(TodoListContractMapper::toContract)
            .getOrThrow(::mapListErrorToException)
        call.respond(HttpStatusCode.Created, result)
    }

    override suspend fun getTodoList(
        listId: String,
        call: TypedApplicationCall<TodoListResponseContract>,
    ) {
        val userId = call.requirePrincipal().userId
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
        val userId = call.requirePrincipal().userId
        val result = todoListService.updateTodoList(
            userId,
            TodoListContractMapper.toDomain(listId, updateTodoListRequest),
        )
            .map(TodoListContractMapper::toContract)
            .getOrThrow(::mapListErrorToException)
        call.respondTyped(result)
    }

    override suspend fun deleteTodoList(listId: String, call: ApplicationCall) {
        val userId = call.requirePrincipal().userId
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
        val userId = call.requirePrincipal().userId
        val listUuid = TodoListIdMapper.toDomain(listId)
        val result = todoService.listTodos(userId, listUuid, pageSize ?: DEFAULT_PAGE_SIZE, page ?: 1, completed)
            .map(TodoContractMapper::toContract)
            .getOrThrow(TodoController::mapErrorToException)
        call.respondTyped(result)
    }

    override suspend fun createTodoInList(
        listId: String,
        createTodoRequest: CreateTodoRequestContract,
        call: TypedApplicationCall<TodoResponseContract>,
    ) {
        val userId = call.requirePrincipal().userId
        val listUuid = TodoListIdMapper.toDomain(listId)
        val result = todoService.createTodo(userId, listUuid, TodoContractMapper.toDomain(createTodoRequest))
            .map(TodoContractMapper::toContract)
            .getOrThrow(TodoController::mapErrorToException)
        call.respond(HttpStatusCode.Created, result)
    }

    override suspend fun getTodoInList(
        listId: String,
        todoId: String,
        call: TypedApplicationCall<TodoResponseContract>,
    ) {
        val userId = call.requirePrincipal().userId
        val listUuid = TodoListIdMapper.toDomain(listId)
        val result = todoService.getTodo(userId, listUuid, TodoIdMapper.toDomain(todoId))
            .map(TodoContractMapper::toContract)
            .getOrThrow(TodoController::mapErrorToException)
        call.respondTyped(result)
    }

    override suspend fun updateTodoInList(
        listId: String,
        todoId: String,
        updateTodoRequest: UpdateTodoRequestContract,
        call: TypedApplicationCall<TodoResponseContract>,
    ) {
        val userId = call.requirePrincipal().userId
        val listUuid = TodoListIdMapper.toDomain(listId)
        val result = todoService.updateTodo(userId, listUuid, TodoContractMapper.toDomain(todoId, updateTodoRequest))
            .map(TodoContractMapper::toContract)
            .getOrThrow(TodoController::mapErrorToException)
        call.respondTyped(result)
    }

    override suspend fun deleteTodoInList(
        listId: String,
        todoId: String,
        call: ApplicationCall,
    ) {
        val userId = call.requirePrincipal().userId
        val listUuid = TodoListIdMapper.toDomain(listId)
        todoService.deleteTodo(userId, listUuid, TodoIdMapper.toDomain(todoId))
            .getOrThrow(TodoController::mapErrorToException)
        call.respond(HttpStatusCode.NoContent)
    }

    companion object {
        private const val DEFAULT_PAGE_SIZE = 20

        private fun mapListErrorToException(error: TodoListServiceError): Throwable = when (error) {
            is TodoListServiceError.TodoListNotFound -> ProblemDetailsException(
                type = ProblemDetailsDefaults.NotFound.TYPE,
                statusCode = HttpStatusCode.NotFound,
                message = todoListNotFoundMessage(error.id),
                cause = null,
            )

            is TodoListServiceError.ValidationFailed -> ProblemDetailsException(
                type = ProblemDetailsDefaults.ValidationFailed.TYPE,
                statusCode = HttpStatusCode.UnprocessableEntity,
                message = ProblemDetailsDefaults.ValidationFailed.MESSAGE,
                cause = null,
                errors = error.errors
                    .groupBy { it.path.trimStart('.') }
                    .mapValues { (_, errs) -> errs.map { it.message } },
            )

            is TodoListServiceError.Forbidden -> ProblemDetailsException(
                type = ProblemDetailsDefaults.Forbidden.TYPE,
                statusCode = HttpStatusCode.Forbidden,
                message = error.resource.toForbiddenMessage(error.permission),
                cause = null,
            )

            is TodoListServiceError.UnhandledServiceError -> RuntimeException(
                "Unexpected exception",
                error.t,
            )
        }
    }
}
