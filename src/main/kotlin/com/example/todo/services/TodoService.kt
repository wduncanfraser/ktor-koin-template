package com.example.todo.services

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
import com.example.todo.domain.Todo
import com.example.todo.domain.TodoForCreate
import com.example.todo.domain.TodoForUpdate
import com.example.todo.repository.TodoRepository
import com.example.todo.validation.validate
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jooq.DSLContext
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Owns authorization for todos: bulk operations ([listTodos], [createTodo]) check against the parent
 * [AuthorizationResource.TodoList], while operations on an existing todo ([getTodo], [updateTodo],
 * [deleteTodo]) check against the specific [AuthorizationResource.Todo]. Callers (controllers) do
 * not perform their own checks.
 */
class TodoService(
    private val ctx: DSLContext,
    private val todoRepository: TodoRepository,
    private val authorizationService: AuthorizationService,
) {
    /**
     * Resolves the caller's authorized lists via [AuthorizationService] before opening the DB
     * transaction below — an external call has no business holding a pooled DB connection open.
     * The transaction exists only so the count and paged-select queries run against one consistent
     * snapshot of the (already-resolved) authorized id set.
     */
    suspend fun listAllTodos(
        userId: String,
        pageSize: Int,
        page: Int,
        completed: Boolean? = null,
    ): TodoServiceResult<Page<Todo>> = coroutineBinding {
        val authorizedListIds = authorizationService.listResourceIds(
            userId = userId,
            permission = Permission.Common.CAN_READ,
            resourceType = AuthorizationResourceType.TodoList,
        ).mapError { it.toServiceError() }.bind()

        ctx.resultTransactionCoroutine { c ->
            todoRepository.listByAuthorizedListIds(c.dsl(), authorizedListIds, pageSize, page, completed)
                .mapError { it.toServiceError() }
        }.bind()
    }

    /**
     * Runs in a transaction to ensure list and count are consistent.
     */
    suspend fun listTodos(
        userId: String,
        todoListId: UUID,
        pageSize: Int,
        page: Int,
        completed: Boolean? = null,
    ): TodoServiceResult<Page<Todo>> = ctx.resultTransactionCoroutine { c ->
        coroutineBinding {
            authorizationService.check(userId, Permission.Common.CAN_READ, AuthorizationResource.TodoList(todoListId))
                .mapError { it.toServiceError(TodoServiceError.TodoListNotFound(todoListId)) }
                .bind()
            todoRepository.list(c.dsl(), todoListId, pageSize, page, completed)
                .mapError { it.toServiceError() }
                .bind()
        }
    }

    /**
     * Returns [TodoServiceError.TodoNotFound] if no [Todo] was found.
     */
    suspend fun getTodo(userId: String, todoListId: UUID, id: UUID): TodoServiceResult<Todo> =
        coroutineBinding {
            authorizationService.check(userId, Permission.Common.CAN_READ, AuthorizationResource.Todo(id, todoListId))
                .mapError { it.toServiceError(TodoServiceError.TodoNotFound(id)) }
                .bind()
            todoRepository.getById(ctx, todoListId, id)
                .mapError { it.toServiceError(TodoServiceError.TodoNotFound(id)) }
                .bind()
        }

    /**
     * Writes the `parent_list` tuple in the same transaction as the row insert — if the tuple write
     * fails, the whole creation rolls back rather than leaving a committed todo nobody can reach.
     */
    suspend fun createTodo(
        userId: String,
        todoListId: UUID,
        todoForCreate: TodoForCreate,
    ): TodoServiceResult<Todo> = ctx.resultTransactionCoroutine { c ->
        coroutineBinding {
            todoForCreate.validate()
                .mapError { TodoServiceError.ValidationFailed(it) }
                .bind()
            authorizationService.check(
                userId, Permission.Common.CAN_WRITE, AuthorizationResource.TodoList(todoListId),
            )
                .mapError { it.toServiceError(TodoServiceError.TodoListNotFound(todoListId)) }
                .bind()
            val todo = todoForCreate.toPersistenceModel(todoListId, userId)
            val savedTodo = todoRepository.upsert(c, todo).mapError { it.toServiceError() }.bind()
            authorizationService.writeTuples(listOf(
                AuthorizationTuple.ResourceRelation(
                    child = AuthorizationResource.Todo(savedTodo.id, todoListId),
                    relation = "parent_list",
                    parent = AuthorizationResource.TodoList(todoListId),
                )
            )).mapError { it.toServiceError() }.bind()
            savedTodo
        }
    }

    /**
     * Completed date is only set if not already completed.
     */
    suspend fun updateTodo(
        userId: String,
        todoListId: UUID,
        todoForUpdate: TodoForUpdate,
    ): TodoServiceResult<Todo> = ctx.resultTransactionCoroutine { c ->
        coroutineBinding {
            todoForUpdate.validate()
                .mapError { TodoServiceError.ValidationFailed(it) }
                .bind()
            authorizationService.check(
                userId, Permission.Common.CAN_WRITE, AuthorizationResource.Todo(todoForUpdate.id, todoListId),
            )
                .mapError { it.toServiceError(TodoServiceError.TodoNotFound(todoForUpdate.id)) }
                .bind()
            val todo = todoRepository.getById(c.dsl(), todoListId, todoForUpdate.id, lockRecords = true)
                .mapError { it.toServiceError(notFoundError = TodoServiceError.TodoNotFound(todoForUpdate.id)) }
                .bind()
            val updatedTodo = todo.toPersistenceModel().apply { update(todoForUpdate) }
            todoRepository.upsert(c, updatedTodo)
                .mapError { it.toServiceError() }
                .bind()
        }
    }

    /**
     * Deletes the `parent_list` tuple in the same transaction as the row delete — if the tuple
     * delete fails, the row delete rolls back too, rather than leaving an orphaned tuple behind.
     */
    suspend fun deleteTodo(
        userId: String,
        todoListId: UUID,
        id: UUID,
    ): Result<Unit, TodoServiceError> = ctx.resultTransactionCoroutine { c ->
        coroutineBinding {
            authorizationService.check(
                userId, Permission.Common.CAN_DELETE, AuthorizationResource.Todo(id, todoListId),
            )
                .mapError { it.toServiceError(TodoServiceError.TodoNotFound(id)) }
                .bind()
            todoRepository.delete(c, todoListId, id)
                .mapError { it.toServiceError(TodoServiceError.TodoNotFound(id)) }
                .bind()
            authorizationService.deleteTuples(listOf(
                AuthorizationTuple.ResourceRelation(
                    child = AuthorizationResource.Todo(id, todoListId),
                    relation = "parent_list",
                    parent = AuthorizationResource.TodoList(todoListId),
                )
            )).mapError { it.toServiceError() }.bind()
        }
    }

    companion object {
        /**
         * Converts a [RepositoryError] to a [TodoServiceError]. Takes an optional parameter for explicitly
         * setting the error if the underlying Repository Error is [RepositoryError.RecordNotFound].
         */
        private fun RepositoryError.toServiceError(
            notFoundError: TodoServiceError? = null,
        ): TodoServiceError {
            return when (this) {
                RepositoryError.RecordNotFound -> {
                    notFoundError ?: run {
                        logger.error { "Unexpected return of RecordNotFound" }
                        TodoServiceError.UnhandledServiceError(null)
                    }
                }

                is RepositoryError.RecordConstraintViolation -> TodoServiceError.UnhandledServiceError(this.t)
                is RepositoryError.LockTimeout -> TodoServiceError.UnhandledServiceError(this.t)
                is RepositoryError.UnhandledException -> TodoServiceError.UnhandledServiceError(this.t)
            }
        }

        /**
         * Converts an [AuthorizationError] to a [TodoServiceError]. Takes an optional parameter for explicitly
         * setting the error if the underlying Authorization Error is [AuthorizationError.NotFound].
         */
        private fun AuthorizationError.toServiceError(
            notFoundError: TodoServiceError? = null,
        ): TodoServiceError = when (this) {
            AuthorizationError.NotFound -> notFoundError ?: run {
                logger.error { "Unexpected return of AuthorizationError.NotFound" }
                TodoServiceError.UnhandledServiceError(null)
            }
            is AuthorizationError.Forbidden -> TodoServiceError.Forbidden(this.resource, this.permission)
            is AuthorizationError.CheckFailed -> TodoServiceError.UnhandledServiceError(this.t)
            is AuthorizationError.WriteFailed -> TodoServiceError.UnhandledServiceError(this.t)
        }
    }
}

sealed class TodoServiceError {
    data class TodoNotFound(val id: UUID) : TodoServiceError()
    data class TodoListNotFound(val id: UUID) : TodoServiceError()
    data class ValidationFailed(val errors: ValidationErrors) : TodoServiceError()
    data class Forbidden(val resource: AuthorizationResource, val permission: Permission) : TodoServiceError()
    data class UnhandledServiceError(val t: Throwable?) : TodoServiceError()
}

typealias TodoServiceResult<T> = Result<T, TodoServiceError>
