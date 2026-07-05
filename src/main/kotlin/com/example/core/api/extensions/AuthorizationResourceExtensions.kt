package com.example.core.api.extensions

import com.example.core.authorization.AuthorizationResource
import com.example.core.authorization.Permission

/**
 * Formats a resource's id the same way across every controller that reports it in a Problem Details
 * message, so Controllers never drift into inconsistent wording.
 */
fun AuthorizationResource.toIdLabel(): String = when (this) {
    is AuthorizationResource.TodoList -> "listId=$id"
    is AuthorizationResource.Todo -> "todoId=$id"
}

/**
 * Shared 403 message body, shown when [com.example.core.authorization.AuthorizationService.check]
 * resolves to [com.example.core.authorization.AuthorizationError.Forbidden].
 */
fun AuthorizationResource.toForbiddenMessage(permission: Permission): String =
    "Forbidden: missing '${permission.relation}' on ${toIdLabel()}"
