package com.example.todo.domain

import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Canonical model for a Todo item
 */
data class Todo(
    val id: UUID,
    val name: String,
    val completedAt: Instant?,
    val todoListId: UUID,
    val createdByUserId: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun toPersistenceModel() = TodoForSave(
        id = id,
        name = name,
        completedAt = completedAt,
        todoListId = todoListId,
        createdByUserId = createdByUserId,
    )
}

/**
 * Model for todo create business logic
 */
data class TodoForCreate(
    val name: String,
) {
    fun toPersistenceModel(todoListId: UUID, createdByUserId: String) = TodoForSave(
        id = UUID.randomUUID(),
        name = name,
        completedAt = null,
        todoListId = todoListId,
        createdByUserId = createdByUserId,
    )
}

/**
 * Model for todo update business logic
 */
data class TodoForUpdate(
    val id: UUID,
    val name: String,
    val completed: Boolean?,
)

/**
 * Model for persisting a todo item
 */
data class TodoForSave(
    val id: UUID,
    var name: String,
    var completedAt: Instant?,
    val todoListId: UUID,
    val createdByUserId: String,
) {
    fun update(update: TodoForUpdate) {
        name = update.name
        completedAt = if (update.completed == false) {
            null
        } else {
            completedAt ?: Clock.System.now()
        }
    }
}
