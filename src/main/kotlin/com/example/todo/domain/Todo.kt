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
    val modifiedAt: Instant,
    val createdAt: Instant,
) {
    fun toPersistenceModel() = TodoForSave(
        id = id,
        name = name,
        completedAt = completedAt,
    )
}

/**
 * Model for todo create business logic
 */
data class TodoForCreate(
    val name: String,
) {
    fun toPersistenceModel() = TodoForSave(
        id = UUID.randomUUID(),
        name = name,
        completedAt = null,
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
