package com.example.todolist.repository.mappers

import com.example.generated.db.tables.records.TodoListRecord
import com.example.todolist.domain.TodoList
import com.example.todolist.domain.TodoListForSave
import kotlin.time.toKotlinInstant

/**
 * Repository mappers are used to convert from our jOOQ models to Domain models, and vice versa.
 */
object TodoListMapper {
    fun toDomain(record: TodoListRecord) = TodoList(
        id = record.id!!,
        name = record.name!!,
        description = record.description,
        createdByUserId = record.createdByUserId!!,
        createdAt = record.createdAt!!.toKotlinInstant(),
        updatedAt = record.updatedAt!!.toKotlinInstant(),
    )

    fun toRecord(todoList: TodoListForSave) = TodoListRecord(
        id = todoList.id,
        name = todoList.name,
        description = todoList.description,
        createdByUserId = todoList.createdByUserId,
    )
}
