package com.example.todo.services

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

class TodoService(
    private val ctx: DSLContext,
    private val todoRepository: TodoRepository,
) {
    /**
     * Runs in a transaction to ensure list and count are consistent.
     */
    suspend fun listAllTodos(
        createdByUserId: String,
        pageSize: Int,
        page: Int,
        completed: Boolean? = null,
    ): TodoServiceResult<Page<Todo>> = ctx.resultTransactionCoroutine { c ->
        todoRepository.listByUser(c.dsl(), createdByUserId, pageSize, page, completed)
            .mapError { it.toServiceError() }
    }

    /**
     * Runs in a transaction to ensure list and count are consistent.
     */
    suspend fun listTodos(
        todoListId: UUID,
        pageSize: Int,
        page: Int,
        completed: Boolean? = null,
    ): TodoServiceResult<Page<Todo>> = ctx.resultTransactionCoroutine { c ->
        todoRepository.list(c.dsl(), todoListId, pageSize, page, completed)
            .mapError { it.toServiceError() }
    }

    /**
     * Returns [TodoServiceError.TodoNotFound] if no [Todo] was found.
     */
    suspend fun getTodo(todoListId: UUID, id: UUID): TodoServiceResult<Todo> {
        return todoRepository.getById(ctx, todoListId, id)
            .mapError { it.toServiceError(TodoServiceError.TodoNotFound(id)) }
    }

    suspend fun createTodo(
        todoListId: UUID,
        createdByUserId: String,
        todoForCreate: TodoForCreate,
    ): TodoServiceResult<Todo> = ctx.resultTransactionCoroutine { c ->
        coroutineBinding {
            todoForCreate.validate()
                .mapError { TodoServiceError.ValidationFailed(it) }
                .bind()
            val todo = todoForCreate.toPersistenceModel(todoListId, createdByUserId)
            todoRepository.upsert(c, todo).mapError { it.toServiceError() }.bind()
        }
    }

    /**
     * Completed date is only set if not already completed.
     */
    suspend fun updateTodo(
        todoListId: UUID,
        todoForUpdate: TodoForUpdate,
    ): TodoServiceResult<Todo> = ctx.resultTransactionCoroutine { c ->
        coroutineBinding {
            todoForUpdate.validate()
                .mapError { TodoServiceError.ValidationFailed(it) }
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

    suspend fun deleteTodo(
        todoListId: UUID,
        id: UUID,
    ): Result<Unit, TodoServiceError> = ctx.resultTransactionCoroutine { c ->
        todoRepository.delete(c, todoListId, id)
            .mapError { it.toServiceError(TodoServiceError.TodoNotFound(id)) }
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
    }
}

sealed class TodoServiceError {
    data class TodoNotFound(val id: UUID) : TodoServiceError()
    data class ValidationFailed(val errors: ValidationErrors) : TodoServiceError()
    data class UnhandledServiceError(val t: Throwable?) : TodoServiceError()
}

typealias TodoServiceResult<T> = Result<T, TodoServiceError>
