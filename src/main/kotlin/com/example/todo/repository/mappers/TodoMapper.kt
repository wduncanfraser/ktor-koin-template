package com.example.todo.repository.mappers

import com.example.generated.db.tables.records.TodoRecord
import com.example.todo.domain.Todo
import com.example.todo.domain.TodoForSave
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

/**
 * Repository mappers are used to convert from our Jooq models to Domain models, and vice versa.
 */
object TodoMapper {
    /**
     * Convert a jooq [TodoRecord] instance into a [Todo]
     */
    fun toDomain(record: TodoRecord) = Todo(
        id = record.id!!,
        name = record.name!!,
        completedAt = record.completedAt?.toKotlinInstant(),
        createdAt = record.createdAt!!.toKotlinInstant(),
        modifiedAt = record.modifiedAt!!.toKotlinInstant(),
    )

    /**
     * Convert a [TodoForSave] instance into a [TodoRecord].
     */
    fun toRecord(todo: TodoForSave) = TodoRecord(
        id = todo.id,
        name = todo.name,
        completedAt = todo.completedAt?.toJavaInstant(),
    )
}
