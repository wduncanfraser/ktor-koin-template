package com.example.todolist.domain

import java.util.UUID
import kotlin.time.Instant

/**
 * Canonical model for a TodoList
 */
data class TodoList(
    val id: UUID,
    val name: String,
    val description: String?,
    val createdByUserId: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun toPersistenceModel() = TodoListForSave(
        id = id,
        name = name,
        description = description,
        createdByUserId = createdByUserId,
    )
}

/**
 * Model for todo list create business logic
 */
data class TodoListForCreate(
    val name: String,
    val description: String?,
) {
    fun toPersistenceModel(createdByUserId: String) = TodoListForSave(
        id = UUID.randomUUID(),
        name = name,
        description = description,
        createdByUserId = createdByUserId,
    )
}

/**
 * Model for todo list update business logic. A null [description] explicitly clears the field.
 */
data class TodoListForUpdate(
    val id: UUID,
    val name: String,
    val description: String?,
)

/**
 * Model for persisting a todo list
 */
data class TodoListForSave(
    val id: UUID,
    var name: String,
    var description: String?,
    val createdByUserId: String,
) {
    fun update(update: TodoListForUpdate) {
        name = update.name
        description = update.description
    }
}
