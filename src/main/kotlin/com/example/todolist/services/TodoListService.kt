package com.example.todolist.services

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

class TodoListService(
    private val ctx: DSLContext,
    private val todoListRepository: TodoListRepository,
) {
    /**
     * Runs in a transaction to ensure list and count are consistent.
     */
    suspend fun listTodoLists(
        userId: String,
        pageSize: Int,
        page: Int,
    ): TodoListServiceResult<Page<TodoList>> = ctx.resultTransactionCoroutine { c ->
        todoListRepository.list(c.dsl(), userId, pageSize, page)
            .mapError { it.toServiceError() }
    }

    /**
     * Returns [TodoListServiceError.TodoListNotFound] if no [TodoList] was found.
     */
    suspend fun getTodoList(userId: String, id: UUID): TodoListServiceResult<TodoList> {
        return todoListRepository.getById(ctx, userId, id)
            .mapError { it.toServiceError(TodoListServiceError.TodoListNotFound(id)) }
    }

    suspend fun createTodoList(
        userId: String,
        todoListForCreate: TodoListForCreate,
    ): TodoListServiceResult<TodoList> = ctx.resultTransactionCoroutine { c ->
        coroutineBinding {
            todoListForCreate.validate()
                .mapError { TodoListServiceError.ValidationFailed(it) }
                .bind()
            val todoList = todoListForCreate.toPersistenceModel(userId)
            todoListRepository.upsert(c, todoList).mapError { it.toServiceError() }.bind()
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
            val todoList = todoListRepository.getById(c.dsl(), userId, todoListForUpdate.id, lockRecords = true)
                .mapError { it.toServiceError(TodoListServiceError.TodoListNotFound(todoListForUpdate.id)) }
                .bind()
            val updatedTodoList = todoList.toPersistenceModel().apply { update(todoListForUpdate) }
            todoListRepository.upsert(c, updatedTodoList)
                .mapError { it.toServiceError() }
                .bind()
        }
    }

    suspend fun deleteTodoList(
        userId: String,
        id: UUID,
    ): TodoListServiceResult<Unit> = ctx.resultTransactionCoroutine { c ->
        todoListRepository.delete(c, userId, id)
            .mapError { it.toServiceError(TodoListServiceError.TodoListNotFound(id)) }
    }

    companion object {
        /**
         * Converts a [RepositoryError] to a [TodoListServiceError]. Takes an optional parameter for explicitly
         * setting the error if the underlying Repository Error is [RepositoryError.RecordNotFound].
         */
        private fun RepositoryError.toServiceError(
            notFoundError: TodoListServiceError? = null,
        ): TodoListServiceError {
            return when (this) {
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
        }
    }
}

sealed class TodoListServiceError {
    data class TodoListNotFound(val id: UUID) : TodoListServiceError()
    data class ValidationFailed(val errors: ValidationErrors) : TodoListServiceError()
    data class UnhandledServiceError(val t: Throwable?) : TodoListServiceError()
}

typealias TodoListServiceResult<T> = Result<T, TodoListServiceError>
