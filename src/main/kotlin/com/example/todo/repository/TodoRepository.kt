package com.example.todo.repository

import com.example.core.domain.Page
import com.example.core.repository.RepositoryConsts
import com.example.core.repository.RepositoryResult
import com.example.core.repository.PaginationUtil
import com.example.core.repository.mapExpectingOne
import com.example.core.repository.runWrappingError
import com.example.core.repository.toNotFoundIfNull
import com.example.generated.db.tables.references.TODO
import com.example.todo.domain.Todo
import com.example.todo.domain.TodoForSave
import com.example.todo.repository.mappers.TodoMapper
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
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
     * Must be called within a transaction to ensure the count and list results are consistent.
     */
    suspend fun list(
        ctx: DSLContext,
        todoListId: UUID,
        pageSize: Int,
        page: Int,
        completed: Boolean? = null,
    ): RepositoryResult<Page<Todo>> {
        var conditions = TODO.TODO_LIST_ID.eq(todoListId)
        if (completed == true) {
            conditions = conditions.and(TODO.COMPLETED_AT.isNotNull)
        } else if (completed == false) {
            conditions = conditions.and(TODO.COMPLETED_AT.isNull)
        }
        return listPaged(ctx, conditions, pageSize, page)
    }

    /**
     * Must be called within a transaction to ensure the count and list results are consistent.
     */
    suspend fun listByUser(
        ctx: DSLContext,
        createdByUserId: String,
        pageSize: Int,
        page: Int,
        completed: Boolean? = null,
    ): RepositoryResult<Page<Todo>> {
        var conditions = TODO.CREATED_BY_USER_ID.eq(createdByUserId)
        if (completed == true) {
            conditions = conditions.and(TODO.COMPLETED_AT.isNotNull)
        } else if (completed == false) {
            conditions = conditions.and(TODO.COMPLETED_AT.isNull)
        }
        return listPaged(ctx, conditions, pageSize, page)
    }

    private suspend fun listPaged(
        ctx: DSLContext,
        conditions: Condition,
        pageSize: Int,
        page: Int,
    ): RepositoryResult<Page<Todo>> = runWrappingError {
        val totalRows = ctx.selectCount()
            .from(TODO)
            .where(conditions)
            .awaitSingle()
            .get(0, Int::class.java)
        val totalPages = PaginationUtil.calculateTotalPages(totalRows, pageSize)
        val offset = PaginationUtil.calculateOffset(page, pageSize)

        val data = ctx.selectFrom(TODO)
            .where(conditions)
            .orderBy(TODO.CREATED_AT.asc())
            .limit(pageSize)
            .offset(offset)
            .asFlow()
            .map(TodoMapper::toDomain)
            .toList()

        Page(
            data = data,
            pageNumber = page,
            pageSize = pageSize,
            totalRows = totalRows,
            totalPages = totalPages,
        )
    }

    /**
     * Returns [com.example.core.repository.RepositoryError.RecordNotFound] if no [Todo] was found.
     */
    suspend fun getById(
        ctx: DSLContext,
        todoListId: UUID,
        id: UUID,
        lockRecords: Boolean = false,
        lockWait: Duration = RepositoryConsts.DEFAULT_LOCK_TIMEOUT,
    ): RepositoryResult<Todo> = runWrappingError {
        // TODO: Wait with R2DBC leads to failed queries, https://github.com/jOOQ/jOOQ/issues/19681
        //  Workaround is to set lock_timeout as a separate statement
        if (lockRecords) {
            ctx.setLocal("lock_timeout", DSL.inline(lockWait.toMillis().toString())).awaitFirstOrNull()
        }
        ctx.selectFrom(TODO)
            .where(TODO.ID.eq(id).and(TODO.TODO_LIST_ID.eq(todoListId)))
            .apply {
                if (lockRecords) {
                    this.forUpdate()
                    // TODO: Wait with R2DBC leads to failed queries
                    //    .wait(lockWait.seconds.toInt())
                }
            }
            .awaitFirstOrNull()
            ?.let(TodoMapper::toDomain)
    }.toNotFoundIfNull()

    suspend fun upsert(
        c: Configuration,
        todo: TodoForSave,
    ): RepositoryResult<Todo> = runWrappingError {
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

    /**
     * Returns [com.example.core.repository.RepositoryError.RecordNotFound] if nothing was deleted.
     */
    suspend fun delete(
        c: Configuration,
        todoListId: UUID,
        id: UUID,
    ): RepositoryResult<Unit> = runWrappingError {
        c.dsl()
            .deleteFrom(TODO)
            .where(TODO.ID.eq(id).and(TODO.TODO_LIST_ID.eq(todoListId)))
            .awaitSingle()
    }.mapExpectingOne()

}
