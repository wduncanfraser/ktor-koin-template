package com.example.todo.repository.mappers

import com.example.generated.db.tables.records.TodoRecord
import com.example.todo.domain.Todo
import com.example.todo.domain.TodoForSave
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

/**
 * Repository mappers are used to convert from our jOOQ models to Domain models, and vice versa.
 */
object TodoMapper {
    fun toDomain(record: TodoRecord) = Todo(
        id = record.id!!,
        name = record.name!!,
        completedAt = record.completedAt?.toKotlinInstant(),
        todoListId = record.todoListId!!,
        createdByUserId = record.createdByUserId!!,
        createdAt = record.createdAt!!.toKotlinInstant(),
        updatedAt = record.updatedAt!!.toKotlinInstant(),
    )

    fun toRecord(todo: TodoForSave) = TodoRecord(
        id = todo.id,
        name = todo.name,
        completedAt = todo.completedAt?.toJavaInstant(),
        todoListId = todo.todoListId,
        createdByUserId = todo.createdByUserId,
    )
}
