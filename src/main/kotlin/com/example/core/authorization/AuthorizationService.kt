package com.example.core.authorization

import com.github.michaelbull.result.Result
import java.util.UUID

/**
 * Authorization boundary for checking permissions and managing relationship tuples. [check]
 * evaluates whether a user has a given [Permission] on a [AuthorizationResource]; [writeTuples] and
 * [deleteTuples] manage the [AuthorizationTuple] assignments and structural links that [check]
 * resolves against. This interface is backend-agnostic — nothing here is specific to any one
 * ReBAC/RBAC implementation.
 */
interface AuthorizationService {
    /**
     * Checks that [userId] has [permission] on [resource].
     *
     * Returns [AuthorizationError.NotFound] if the user has no access to the resource at all.
     * Returns [AuthorizationError.Forbidden] if the user can view the resource but lacks
     * the required permission for this specific action.
     * Returns [AuthorizationError.CheckFailed] if the check itself could not be completed due to
     * an infrastructure or configuration failure.
     */
    suspend fun check(
        userId: String,
        permission: Permission,
        resource: AuthorizationResource,
    ): Result<Unit, AuthorizationError>

    /**
     * Lists the ids of every [AuthorizationResource] of [resourceType] that [userId] has
     * [permission] on — e.g. every resource a user can read, including ones shared with them, not
     * just ones they created.
     *
     * Returns an empty list (not an error) if the user has [permission] on nothing; there is no single resource here
     * whose access could be [AuthorizationError.NotFound]/[AuthorizationError.Forbidden].
     * Returns [AuthorizationError.CheckFailed] if the query itself could not be completed.
     */
    suspend fun listResourceIds(
        userId: String,
        permission: Permission,
        resourceType: AuthorizationResourceType,
    ): Result<List<UUID>, AuthorizationError>

    /**
     * Deletes every relationship tuple in which [resource] participates — both relations assigned
     * directly on it (e.g. owner/editor/viewer) and structural links from other resources that
     * point to it (e.g. a child's parent link). Call when a resource is destroyed so no tuples are
     * orphaned. Idempotent: deleting a tuple that no longer exists is not an error.
     *
     * Ordering guarantee: structural links pointing *at* the resource are removed before the
     * relations held *directly on* the resource. So if the deletion fails partway, the resource's
     * own access grants (which include whatever grants the caller the permission to delete it) are
     * the last to go — a caller is never left unable to retry because it deleted its own delete
     * permission early.
     *
     * Returns [AuthorizationError.WriteFailed] if the deletion could not be completed.
     */
    suspend fun deleteAllTuplesFor(resource: AuthorizationResource): Result<Unit, AuthorizationError>

    /**
     * Writes relationship tuples to the authorization store.
     * Called on resource creation to assign ownership and structural links.
     */
    suspend fun writeTuples(tuples: List<AuthorizationTuple>): Result<Unit, AuthorizationError>

    /**
     * Deletes relationship tuples from the authorization store.
     * Called on resource deletion to clean up ownership and structural links.
     */
    suspend fun deleteTuples(tuples: List<AuthorizationTuple>): Result<Unit, AuthorizationError>
}

/**
 * Error returned when an authorization operation fails.
 *
 * [NotFound] maps to 404 — the resource appears not to exist (user has no viewer access or higher).
 * [Forbidden] maps to 403 — the user can view the resource but lacks permission for this action.
 * [CheckFailed] means [AuthorizationService.check] or [AuthorizationService.listResourceIds] itself
 * could not be completed (e.g. an infrastructure outage, or a relation/type combination not defined
 * in the backing model) — this is an infrastructure/configuration failure, not a permission outcome,
 * and must not be treated as "no access".
 * [WriteFailed] indicates [AuthorizationService.writeTuples] or [AuthorizationService.deleteTuples]
 * failed — this is an infrastructure failure, not a permission outcome.
 */
sealed class AuthorizationError {
    data object NotFound : AuthorizationError()
    data class Forbidden(val resource: AuthorizationResource, val permission: Permission) : AuthorizationError()
    data class CheckFailed(val t: Throwable?) : AuthorizationError()
    data class WriteFailed(val t: Throwable?) : AuthorizationError()
}
