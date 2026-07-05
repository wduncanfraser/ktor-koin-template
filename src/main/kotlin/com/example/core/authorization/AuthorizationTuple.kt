package com.example.core.authorization

/**
 * A relationship to assign or remove via [AuthorizationService.writeTuples]/[AuthorizationService.deleteTuples]. Both
 * cases carry a `relation` name that must match one defined in the backing [AuthorizationService] implementation's
 * model.
 *
 * [UserRelation] assigns a relation directly between a user and a [AuthorizationResource] (e.g.
 * granting ownership). [ResourceRelation] links two [AuthorizationResource]s (e.g. a child
 * resource's structural link to its parent) so permissions can be computed transitively instead of
 * being assigned per resource.
 */
sealed class AuthorizationTuple {
    /**
     * Direct user → resource assignment (e.g. this template's `owner`/`editor`/`viewer`) — the
     * specific relation names are defined by the backing [AuthorizationService] implementation's
     * model, not by this type.
     */
    data class UserRelation(
        val userId: String,
        val relation: String,
        val resource: AuthorizationResource,
    ) : AuthorizationTuple()

    /**
     * Structural resource → resource link (e.g. this template's `parent_list`) — again, the
     * relation name is defined by the backing model, not by this type.
     */
    data class ResourceRelation(
        val child: AuthorizationResource,
        val relation: String,
        val parent: AuthorizationResource,
    ) : AuthorizationTuple()
}
