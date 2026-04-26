package com.example.todolist.repository

import com.example.core.domain.Page
import com.example.core.repository.RepositoryConsts
import com.example.core.repository.RepositoryResult
import com.example.core.repository.mapExpectingOne
import com.example.core.repository.runWrappingError
import com.example.core.repository.toNotFoundIfNull
import com.example.generated.db.tables.references.TODO_LIST
import com.example.todolist.domain.TodoList
import com.example.todolist.domain.TodoListForSave
import com.example.todolist.repository.mappers.TodoListMapper
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.jooq.Configuration
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.time.Duration
import java.util.UUID

class TodoListRepository {
    suspend fun list(
        ctx: DSLContext,
        createdByUserId: String,
        pageSize: Int,
        page: Int,
    ): RepositoryResult<Page<TodoList>> = runWrappingError {
        val totalRows = ctx.selectCount()
            .from(TODO_LIST)
            .where(TODO_LIST.CREATED_BY_USER_ID.eq(createdByUserId))
            .awaitSingle()
            .get(0, Int::class.java)
        val totalPages = (totalRows + pageSize - 1) / pageSize
        val offset = (page - 1) * pageSize

        val data = ctx.selectFrom(TODO_LIST)
            .where(TODO_LIST.CREATED_BY_USER_ID.eq(createdByUserId))
            .orderBy(TODO_LIST.CREATED_AT.asc())
            .limit(pageSize)
            .offset(offset)
            .asFlow()
            .map(TodoListMapper::toDomain)
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
     * Returns [com.example.core.repository.RepositoryError.RecordNotFound] if no [TodoList] was found.
     */
    suspend fun getById(
        ctx: DSLContext,
        createdByUserId: String,
        id: UUID,
        lockRecords: Boolean = false,
        lockWait: Duration = RepositoryConsts.DEFAULT_LOCK_TIMEOUT,
    ): RepositoryResult<TodoList> = runWrappingError {
        // TODO: Wait with R2DBC leads to failed queries, https://github.com/jOOQ/jOOQ/issues/19681
        //  Workaround is to set lock_timeout as a separate statement
        if (lockRecords) {
            ctx.setLocal("lock_timeout", DSL.inline(lockWait.toMillis().toString())).awaitFirstOrNull()
        }
        ctx.selectFrom(TODO_LIST)
            .where(TODO_LIST.ID.eq(id).and(TODO_LIST.CREATED_BY_USER_ID.eq(createdByUserId)))
            .apply {
                if (lockRecords) {
                    this.forUpdate()
                    // TODO: Wait with R2DBC leads to failed queries
                    //    .wait(lockWait.seconds.toInt())
                }
            }
            .awaitFirstOrNull()
            ?.let(TodoListMapper::toDomain)
    }.toNotFoundIfNull()

    suspend fun upsert(
        c: Configuration,
        todoList: TodoListForSave,
    ): RepositoryResult<TodoList> = runWrappingError {
        val record = TodoListMapper.toRecord(todoList)
        val result = c.dsl()
            .insertInto(TODO_LIST)
            .set(record)
            .onDuplicateKeyUpdate()
            .set(record)
            .returning()
            .awaitSingle()
        TodoListMapper.toDomain(result)
    }

    /**
     * Returns [com.example.core.repository.RepositoryError.RecordNotFound] if nothing was deleted.
     * Cascade deletes all [com.example.todo.domain.Todo]s belonging to this list.
     */
    suspend fun delete(
        c: Configuration,
        createdByUserId: String,
        id: UUID,
    ): RepositoryResult<Unit> = runWrappingError {
        c.dsl()
            .deleteFrom(TODO_LIST)
            .where(TODO_LIST.ID.eq(id).and(TODO_LIST.CREATED_BY_USER_ID.eq(createdByUserId)))
            .awaitSingle()
    }.mapExpectingOne()
}
