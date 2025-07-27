package com.example.todo.repository

import com.example.core.models.Page
import com.example.core.repository.RepositoryConsts
import com.example.core.repository.RepositoryResult
import com.example.core.repository.mapExpectingOne
import com.example.core.repository.runWrappingError
import com.example.core.repository.toNotFoundIfNull
import com.example.todo.repository.mappers.TodoMapper
import com.example.generated.db.tables.references.TODO
import com.example.todo.domain.Todo
import com.example.todo.domain.TodoForSave
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.jooq.Condition
import org.jooq.Configuration
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.time.Duration
import java.util.UUID

class TodoRepository {
    /**
     * List all [Todo]s based on the given parameters.
     */
    suspend fun list(
        ctx: DSLContext,
        pageSize: Int,
        page: Int,
        completed: Boolean? = null,
    ): RepositoryResult<Page<Todo>> {
        return runWrappingError {
            val totalRows = ctx.selectCount()
                .from(TODO)
                .where(todoConditions(completed))
                .awaitSingle()
                .get(0, Int::class.java)
            val totalPages = (totalRows + pageSize - 1) / pageSize
            val offset = (page - 1) * pageSize

            val data = ctx.selectFrom(TODO)
                .where(todoConditions(completed))
                .orderBy(TODO.CREATED_AT.asc())
                .limit(pageSize)
                .offset(offset)
                .fetchAsync()
                .thenApply { it.map(TodoMapper::toDomain) }
                .await()

            Page(
                data = data,
                pageNumber = page,
                pageSize = pageSize,
                totalRows = totalRows,
                totalPages = totalPages,
            )
        }
    }

    /**
     * Count all [Todo] in the database
     */
    suspend fun count(
        ctx: DSLContext,
        completed: Boolean? = null,
    ): RepositoryResult<Int> {
        return runWrappingError {
            ctx.selectCount()
                .from(TODO)
                .where(todoConditions(completed))
                .awaitSingle()
                .get(0, Int::class.java)
        }
    }

    /**
     * Get a single [Todo] by [id]
     * Returns [com.example.core.repository.RepositoryError.RecordNotFound] if no [Todo] was found.
     */
    suspend fun getById(
        ctx: DSLContext,
        id: UUID,
        lockRecords: Boolean = false,
        lockWait: Duration = RepositoryConsts.DEFAULT_LOCK_TIMEOUT,
    ): RepositoryResult<Todo> {
        return runWrappingError {
            ctx.selectFrom(TODO)
                .where(TODO.ID.eq(id))
                .apply {
                    if (lockRecords) {
                        this.forUpdate()
                            .wait(lockWait.seconds.toInt())
                    }
                }
                .awaitFirstOrNull()
                ?.let(TodoMapper::toDomain)
        }.toNotFoundIfNull()
    }

    /**
     * Insert or update a [Todo] and return the created entity
     */
    suspend fun upsert(
        c: Configuration,
        todo: TodoForSave,
    ): RepositoryResult<Todo> {
        return runWrappingError {
            val record = TodoMapper.toRecord(todo)
            val result = c.dsl()
                .insertInto(TODO)
                .set(record)
                .onDuplicateKeyUpdate()
                .set(record)
                .returning()
                .awaitSingle()

            TodoMapper.toDomain(result)
        }
    }

    /**
     * Delete a given [Todo] by [id]
     * Returns [com.example.core.repository.RepositoryError.RecordNotFound] if nothing was deleted.
     */
    suspend fun delete(
        c: Configuration,
        id: UUID,
    ): RepositoryResult<Unit> {
        return runWrappingError {
            c.dsl()
                .deleteFrom(TODO)
                .where(TODO.ID.eq(id))
                .executeAsync()
                .await()
        }.mapExpectingOne()
    }

    /**
     * Common conditions to a list/count [Todo]s query.
     */
    private fun todoConditions(completed: Boolean?): Condition {
        var conditions = DSL.noCondition()
        if (completed == true) {
            conditions = conditions.and(TODO.COMPLETED_AT.isNotNull)
        } else if (completed == false) {
            conditions = conditions.and(TODO.COMPLETED_AT.isNull)
        }
        return conditions
    }
}
