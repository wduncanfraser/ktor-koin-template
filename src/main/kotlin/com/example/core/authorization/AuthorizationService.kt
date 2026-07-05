package com.example.core.authorization

import com.github.michaelbull.result.Result

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
 * [CheckFailed] means [AuthorizationService.check] itself could not be completed (e.g. an
 * infrastructure outage, or a relation/type combination not defined in the backing model) — this is
 * an infrastructure/configuration failure, not a permission outcome, and must not be treated as "no
 * access".
 * [WriteFailed] indicates [AuthorizationService.writeTuples] or [AuthorizationService.deleteTuples]
 * failed — this is an infrastructure failure, not a permission outcome.
 */
sealed class AuthorizationError {
    data object NotFound : AuthorizationError()
    data class Forbidden(val resource: AuthorizationResource, val permission: Permission) : AuthorizationError()
    data class CheckFailed(val t: Throwable?) : AuthorizationError()
    data class WriteFailed(val t: Throwable?) : AuthorizationError()
}
