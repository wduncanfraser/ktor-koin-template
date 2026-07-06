package com.example.todolist.services

import com.example.core.authorization.AuthorizationError
import com.example.core.authorization.AuthorizationResource
import com.example.core.authorization.AuthorizationResourceType
import com.example.core.authorization.AuthorizationService
import com.example.core.authorization.AuthorizationTuple
import com.example.core.authorization.Permission
import com.example.core.domain.Page
import com.example.core.repository.RepositoryError
import com.example.core.validation.ValidationErrors
import com.example.resultTransactionCoroutine
import com.example.todolist.domain.TodoList
import com.example.todolist.domain.TodoListForCreate
import com.example.todolist.domain.TodoListForUpdate
import com.example.todolist.repository.TodoListRepository
import com.example.todolist.validation.validate
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jooq.DSLContext
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Owns authorization for todo lists themselves.
 */
class TodoListService(
    private val ctx: DSLContext,
    private val todoListRepository: TodoListRepository,
    private val authorizationService: AuthorizationService,
) {
    /**
     * Resolves the caller's authorized lists via [AuthorizationService] before opening the DB
     * transaction below — an external call has no business holding a pooled DB connection open.
     * The transaction exists only so the count and paged-select queries run against one consistent
     * snapshot of the (already-resolved) authorized id set.
     */
    suspend fun listTodoLists(
        userId: String,
        pageSize: Int,
        page: Int,
    ): TodoListServiceResult<Page<TodoList>> = coroutineBinding {
        val authorizedIds = authorizationService.listResourceIds(
            userId = userId,
            permission = Permission.Common.CAN_READ,
            resourceType = AuthorizationResourceType.TodoList,
        ).mapError { it.toServiceError() }.bind()

        ctx.resultTransactionCoroutine { c ->
            todoListRepository.list(c.dsl(), authorizedIds, pageSize, page)
                .mapError { it.toServiceError() }
        }.bind()
    }

    /**
     * Returns [TodoListServiceError.TodoListNotFound] if no [TodoList] was found.
     */
    suspend fun getTodoList(userId: String, id: UUID): TodoListServiceResult<TodoList> =
        coroutineBinding {
            val todoList = todoListRepository.getById(ctx, id)
                .mapError { it.toServiceError(TodoListServiceError.TodoListNotFound(id)) }
                .bind()
            authorizationService.check(
                userId = userId, permission = Permission.Common.CAN_READ,
                resource = AuthorizationResource.TodoList(todoList.id)
            )
                .mapError { it.toServiceError(TodoListServiceError.TodoListNotFound(id)) }
                .bind()
            todoList
        }

    /**
     * Writes the `owner` tuple in the same transaction as the row insert — if the tuple write
     * fails, the whole creation rolls back rather than leaving a committed list nobody can reach.
     */
    suspend fun createTodoList(
        userId: String,
        todoListForCreate: TodoListForCreate,
    ): TodoListServiceResult<TodoList> = ctx.resultTransactionCoroutine { c ->
        coroutineBinding {
            todoListForCreate.validate()
                .mapError { TodoListServiceError.ValidationFailed(it) }
                .bind()
            val todoList = todoListForCreate.toPersistenceModel(userId)
            val savedList = todoListRepository.upsert(c, todoList).mapError { it.toServiceError() }.bind()
            authorizationService.writeTuples(listOf(
                AuthorizationTuple.UserRelation(userId, "owner", AuthorizationResource.TodoList(savedList.id))
            )).mapError { it.toServiceError() }.bind()
            savedList
        }
    }

    suspend fun updateTodoList(
        userId: String,
        todoListForUpdate: TodoListForUpdate,
    ): TodoListServiceResult<TodoList> = ctx.resultTransactionCoroutine { c ->
        coroutineBinding {
            todoListForUpdate.validate()
                .mapError { TodoListServiceError.ValidationFailed(it) }
                .bind()
            val todoList = todoListRepository.getById(c.dsl(), todoListForUpdate.id, lockRecords = true)
                .mapError { it.toServiceError(TodoListServiceError.TodoListNotFound(todoListForUpdate.id)) }
                .bind()
            authorizationService.check(
                userId = userId,
                permission = Permission.Common.CAN_WRITE,
                resource = AuthorizationResource.TodoList(todoList.id)
            )
                .mapError { it.toServiceError(TodoListServiceError.TodoListNotFound(todoListForUpdate.id)) }
                .bind()
            val updatedTodoList = todoList.toPersistenceModel().apply { update(todoListForUpdate) }
            todoListRepository.upsert(c, updatedTodoList)
                .mapError { it.toServiceError() }
                .bind()
        }
    }

    /**
     * Deletes every tuple involving the list (its owner/editor/viewer relations and its todos'
     * `parent_list` links) in the same transaction as the row delete so nothing is orphaned. A tuple-cleanup
     * failure rolls the row delete back rather than committing a half-cleaned deletion.
     */
    suspend fun deleteTodoList(
        userId: String,
        id: UUID,
    ): TodoListServiceResult<Unit> = ctx.resultTransactionCoroutine { c ->
        coroutineBinding {
            val todoList = todoListRepository.getById(c.dsl(), id)
                .mapError { it.toServiceError(TodoListServiceError.TodoListNotFound(id)) }
                .bind()
            authorizationService.check(
                userId = userId,
                permission = Permission.Common.CAN_DELETE,
                resource = AuthorizationResource.TodoList(todoList.id)
            )
                .mapError { it.toServiceError(TodoListServiceError.TodoListNotFound(id)) }
                .bind()
            todoListRepository.delete(c, id).mapError { it.toServiceError() }.bind()
            authorizationService.deleteAllTuplesFor(AuthorizationResource.TodoList(id))
                .mapError { it.toServiceError() }
                .bind()
        }
    }

    companion object {
        /**
         * Converts a [RepositoryError] to a [TodoListServiceError]. Takes an optional parameter for explicitly
         * setting the error if the underlying Repository Error is [RepositoryError.RecordNotFound].
         */
        private fun RepositoryError.toServiceError(
            notFoundError: TodoListServiceError? = null,
        ): TodoListServiceError = when (this) {
            RepositoryError.RecordNotFound -> {
                notFoundError ?: run {
                    logger.error { "Unexpected return of RecordNotFound" }
                    TodoListServiceError.UnhandledServiceError(null)
                }
            }

            is RepositoryError.RecordConstraintViolation -> TodoListServiceError.UnhandledServiceError(this.t)
            is RepositoryError.LockTimeout -> TodoListServiceError.UnhandledServiceError(this.t)
            is RepositoryError.UnhandledException -> TodoListServiceError.UnhandledServiceError(this.t)
        }

        /**
         * Converts an [AuthorizationError] to a [TodoListServiceError]. Takes an optional parameter for explicitly
         * setting the error if the underlying Authorization Error is [AuthorizationError.NotFound].
         */
        private fun AuthorizationError.toServiceError(
            notFoundError: TodoListServiceError? = null,
        ): TodoListServiceError = when (this) {
            AuthorizationError.NotFound -> notFoundError ?: run {
                logger.error { "Unexpected return of AuthorizationError.NotFound" }
                TodoListServiceError.UnhandledServiceError(null)
            }
            is AuthorizationError.Forbidden -> TodoListServiceError.Forbidden(this.resource, this.permission)
            is AuthorizationError.CheckFailed -> TodoListServiceError.UnhandledServiceError(this.t)
            is AuthorizationError.WriteFailed -> TodoListServiceError.UnhandledServiceError(this.t)
        }
    }
}

sealed class TodoListServiceError {
    data class TodoListNotFound(val id: UUID) : TodoListServiceError()
    data class ValidationFailed(val errors: ValidationErrors) : TodoListServiceError()
    data class Forbidden(val resource: AuthorizationResource, val permission: Permission) : TodoListServiceError()
    data class UnhandledServiceError(val t: Throwable?) : TodoListServiceError()
}

typealias TodoListServiceResult<T> = Result<T, TodoListServiceError>
