package com.example.core.authorization

import java.util.UUID

/**
 * Identifies a resource for authorization purposes — what [AuthorizationService.check] resolves a
 * [Permission] against, and what [AuthorizationTuple] entries assign relations to or between.
 *
 * Every case exposes a stable [id]; concrete [AuthorizationService] implementations use that as the resource's
 * identity, so nothing here is specific to any one backend. Feature modules add a case per resource type they
 * want to authorize.
 */
sealed class AuthorizationResource {
    abstract val id: UUID

    data class TodoList(override val id: UUID) : AuthorizationResource()

    /**
     * [todoListId] is not part of this resource's resolved identity — only [id] is used when
     * checking permissions (a todo's permissions resolve via its real parent-list relationship,
     * regardless of what [todoListId] a caller supplies). It exists for constructing structural
     * tuples and is passed through to repository lookups, whose own `id` + `todo_list_id` match is
     * what actually prevents acting on a todo via the wrong list.
     */
    data class Todo(
        override val id: UUID,
        val todoListId: UUID,
    ) : AuthorizationResource()
}

/**
 * Identifies a *type* of [AuthorizationResource], without an id — used by
 * [AuthorizationService.listResourceIds] to ask "which resources of this type does the user have a
 * [Permission] on", where a full [AuthorizationResource] would require an id that's the very thing
 * being asked for. Every [AuthorizationResource] case has a corresponding case here.
 */
enum class AuthorizationResourceType {
    TodoList,
    Todo,
}

val AuthorizationResource.type: AuthorizationResourceType
    get() = when (this) {
        is AuthorizationResource.TodoList -> AuthorizationResourceType.TodoList
        is AuthorizationResource.Todo -> AuthorizationResourceType.Todo
    }
