package com.example.todo.services

import com.example.core.domain.Page
import com.example.core.repository.RepositoryError
import com.example.resultTransactionCoroutine
import com.example.todo.repository.TodoRepository
import com.example.todo.domain.Todo
import com.example.todo.domain.TodoForCreate
import com.example.todo.domain.TodoForUpdate
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
     * Get a list/page of [Todo]s.
     * Runs in a transaction to ensure list and count are consistent.
     */
    suspend fun listTodos(
        pageSize: Int,
        page: Int,
        completed: Boolean? = null,
    ): TodoServiceResult<Page<Todo>> {
        return todoRepository
            .list(ctx, pageSize, page, completed)
            .mapError { it.toServiceError() }
    }

    /**
     * Get a single [Todo] by [id].
     * Returns [TodoServiceError.TodoNotFound] if no [Todo] was found.
     */
    suspend fun getTodo(id: UUID): TodoServiceResult<Todo> {
        return todoRepository.getById(ctx, id)
            .mapError { it.toServiceError(TodoServiceError.TodoNotFound(id)) }
    }

    /**
     * Create a new [Todo] from a [TodoForCreate].
     */
    suspend fun createTodo(
        todoForCreate: TodoForCreate
    ): TodoServiceResult<Todo> {
        val todo = todoForCreate.toPersistenceModel()
        return ctx.resultTransactionCoroutine { c ->
            todoRepository.upsert(c, todo)
                .mapError { it.toServiceError() }
        }
    }

    /**
     * Update an existing [Todo] from a [TodoForUpdate]
     * Completed date is only set if not already completed.
     */
    suspend fun updateTodo(
        todoForUpdate: TodoForUpdate
    ): TodoServiceResult<Todo> = ctx.resultTransactionCoroutine { c ->
        coroutineBinding {
            val todo = todoRepository.getById(c.dsl(), todoForUpdate.id, lockRecords = true)
                .mapError {
                    it.toServiceError(notFoundError = TodoServiceError.TodoNotFound(todoForUpdate.id))
                }
                .bind()

            val updatedTodo = todo.toPersistenceModel().apply { update(todoForUpdate) }

            todoRepository.upsert(c, updatedTodo)
                .mapError { it.toServiceError() }
                .bind()
        }
    }


    /**
     * Delete an existing [Todo].
     */
    suspend fun deleteTodo(
        id: UUID
    ): Result<Unit, TodoServiceError> = ctx.resultTransactionCoroutine { c ->
        todoRepository.delete(c, id)
            .mapError { it.toServiceError(TodoServiceError.TodoNotFound(id)) }
    }

    companion object {
        /**
         * Converts an [RepositoryError] to a [TodoServiceError]. Takes an optional parameter for explicitly
         * setting the error if the underlying Repository Error is [RepositoryError.RecordNotFound]
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
                is RepositoryError.UnhandledException -> TodoServiceError.UnhandledServiceError(this.t)
            }
        }
    }
}

/**
 * TodoService Error type
 */
sealed class TodoServiceError {
    data class TodoNotFound(val id: UUID) : TodoServiceError()
    data class UnhandledServiceError(val t: Throwable?) : TodoServiceError()
}

typealias TodoServiceResult<T> = Result<T, TodoServiceError>
