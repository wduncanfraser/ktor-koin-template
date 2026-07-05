package com.example.core.authorization

/**
 * A permission that can be checked against a resource. Permissions are derived from
 * user–resource relations (e.g. this template's `owner`/`editor`/`viewer`) assigned via
 * [AuthorizationService.writeTuples] — the relation vocabulary itself is defined by the backing
 * model, not by this type.
 *
 * Common permissions are in [Common]; each [relation] must match a relation (or computed relation)
 * actually defined in the backing [AuthorizationService] implementation's model — consult that
 * implementation for the current, authoritative model rather than trusting a copy here. Feature
 * modules with resource-specific permissions (e.g. `can_share`, `can_comment`) should define their
 * own enum implementing this interface.
 */
sealed interface Permission {
    val relation: String

    enum class Common(override val relation: String) : Permission {
        CAN_READ("can_read"),
        CAN_WRITE("can_write"),
        CAN_DELETE("can_delete"),
    }
}
