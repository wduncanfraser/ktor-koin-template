package com.example.core.authorization

import com.example.config.OpenFgaConfig
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import dev.openfga.sdk.api.client.OpenFgaClient
import dev.openfga.sdk.api.client.model.ClientCheckRequest
import dev.openfga.sdk.api.client.model.ClientListObjectsRequest
import dev.openfga.sdk.api.client.model.ClientReadRequest
import dev.openfga.sdk.api.client.model.ClientTupleKey
import dev.openfga.sdk.api.client.model.ClientTupleKeyWithoutCondition
import dev.openfga.sdk.api.client.model.ClientWriteRequest
import dev.openfga.sdk.api.configuration.ClientCheckOptions
import dev.openfga.sdk.api.configuration.ClientConfiguration
import dev.openfga.sdk.api.configuration.ClientListObjectsOptions
import dev.openfga.sdk.api.configuration.ClientReadOptions
import dev.openfga.sdk.api.configuration.ClientWriteOptions
import dev.openfga.sdk.api.model.WriteRequestDeletes
import dev.openfga.sdk.errors.ApiException
import dev.openfga.sdk.errors.FgaInvalidParameterException
import dev.openfga.sdk.errors.FgaValidationError
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import java.util.UUID

private val logger = KotlinLogging.logger {}

// OpenFGA caps a single Write request at OPENFGA_MAX_TUPLES_PER_WRITE tuples (default 100). A bulk
// cleanup can exceed that, so deletes are chunked at this size (see [deleteKeysChunked]).
private const val MAX_TUPLES_PER_WRITE = 100

/**
 * [AuthorizationService] backed by a real OpenFGA store. [check] evaluates the requested
 * [Permission.relation] (e.g. `can_read`/`can_write`/`can_delete`) as computed by the model in
 * `fga/authorization-model.fga` from the tuples assigned via [writeTuples]/[deleteTuples]. Resolves
 * the store id and latest authorization model once at startup (see `init`) so later calls don't
 * re-resolve them per request.
 */
class OpenFgaAuthorizationService(openFgaConfig: OpenFgaConfig) : AuthorizationService {
    private val client: OpenFgaClient
    private val authorizationModelId: String

    init {
        val clientConfig = ClientConfiguration().apiUrl(openFgaConfig.apiUrl)
        client = OpenFgaClient(clientConfig)
        authorizationModelId = runBlocking {
            val stores = client.listStores().await().stores
            val storeId = stores.firstOrNull { it.name == openFgaConfig.storeName }?.id
                ?: error("OpenFGA store '${openFgaConfig.storeName}' not found — run provision before starting the app")
            clientConfig.storeId(storeId)
            val model = client.readLatestAuthorizationModel().await().authorizationModel
            model?.id ?: error(
                "OpenFGA store '${openFgaConfig.storeName}' has no authorization model — run provision first",
            )
        }
    }

    override suspend fun check(
        userId: String,
        permission: Permission,
        resource: AuthorizationResource,
    ): Result<Unit, AuthorizationError> {
        return try {
            if (checkFga(userId, permission, resource)) {
                Ok(Unit)
            } else if (permission == Permission.Common.CAN_READ) {
                // The requested permission itself was "can view" and it was denied — no fallback
                // check can tell us more than that the user has no access at all.
                Err(AuthorizationError.NotFound)
            } else if (checkFga(userId, Permission.Common.CAN_READ, resource)) {
                // Denied the specific permission, but a second check shows they can view the
                // resource — they know it exists and are just missing this one permission.
                Err(AuthorizationError.Forbidden(resource, permission))
            } else {
                // Can't view it either: no access at all, not just missing this one permission.
                Err(AuthorizationError.NotFound)
            }
        } catch (e: ApiException) {
            logger.error(e) { "OpenFGA check failed for ${resource.toFgaObject()}" }
            Err(AuthorizationError.CheckFailed(e))
        } catch (e: FgaInvalidParameterException) {
            logger.error(e) { "OpenFGA check failed for ${resource.toFgaObject()}" }
            Err(AuthorizationError.CheckFailed(e))
        } catch (e: FgaValidationError) {
            logger.error(e) { "OpenFGA check failed for ${resource.toFgaObject()}" }
            Err(AuthorizationError.CheckFailed(e))
        }
    }

    private suspend fun checkFga(userId: String, permission: Permission, resource: AuthorizationResource): Boolean {
        val request = ClientCheckRequest()
            .user("user:$userId")
            .relation(permission.relation)
            ._object(resource.toFgaObject())
        val options = ClientCheckOptions().authorizationModelId(authorizationModelId)
        return client.check(request, options).await().allowed == true
    }

    /**
     * Backed by OpenFGA's non-streamed ListObjects call, which returns a server-capped result set
     * (commonly ~1000 objects) — fine for this template's scale, but not how a production service
     * should do this once a user's accessible set is large relative to that cap. A production
     * implementation should instead maintain its own denormalized "who can see what" index — e.g. a
     * DB join table — kept in sync by consuming OpenFGA's tuple-change feed (`/changes`), and
     * intersect that index with the normal DB query at read time rather than calling ListObjects
     * live per request.
     */
    override suspend fun listResourceIds(
        userId: String,
        permission: Permission,
        resourceType: AuthorizationResourceType,
    ): Result<List<UUID>, AuthorizationError> {
        val request = ClientListObjectsRequest()
            .user("user:$userId")
            .relation(permission.relation)
            .type(resourceType.toFgaType())
        val options = ClientListObjectsOptions().authorizationModelId(authorizationModelId)
        return try {
            val objects = client.listObjects(request, options).await().objects
            Ok(objects.map { UUID.fromString(it.substringAfter(':')) })
        } catch (e: ApiException) {
            logger.error(e) { "OpenFGA listObjects failed for ${resourceType.toFgaType()}" }
            Err(AuthorizationError.CheckFailed(e))
        } catch (e: FgaInvalidParameterException) {
            logger.error(e) { "OpenFGA listObjects failed for ${resourceType.toFgaType()}" }
            Err(AuthorizationError.CheckFailed(e))
        } catch (e: FgaValidationError) {
            logger.error(e) { "OpenFGA listObjects failed for ${resourceType.toFgaType()}" }
            Err(AuthorizationError.CheckFailed(e))
        }
    }

    override suspend fun writeTuples(tuples: List<AuthorizationTuple>): Result<Unit, AuthorizationError> {
        val keys = tuples.map { it.toClientTupleKey() }
        val request = ClientWriteRequest().writes(keys)
        val options = ClientWriteOptions().authorizationModelId(authorizationModelId)
        return try {
            client.write(request, options).await()
            Ok(Unit)
        } catch (e: ApiException) {
            logger.error(e) { "OpenFGA writeTuples failed" }
            Err(AuthorizationError.WriteFailed(e))
        } catch (e: FgaInvalidParameterException) {
            logger.error(e) { "OpenFGA writeTuples failed" }
            Err(AuthorizationError.WriteFailed(e))
        } catch (e: FgaValidationError) {
            logger.error(e) { "OpenFGA writeTuples failed" }
            Err(AuthorizationError.WriteFailed(e))
        }
    }

    override suspend fun deleteTuples(tuples: List<AuthorizationTuple>): Result<Unit, AuthorizationError> {
        val keys = tuples.map { it.toClientTupleKeyWithoutCondition() }
        val request = ClientWriteRequest().deletes(keys)
        val options = ClientWriteOptions().authorizationModelId(authorizationModelId)
        return try {
            client.write(request, options).await()
            Ok(Unit)
        } catch (e: ApiException) {
            logger.error(e) { "OpenFGA deleteTuples failed" }
            Err(AuthorizationError.WriteFailed(e))
        } catch (e: FgaInvalidParameterException) {
            logger.error(e) { "OpenFGA deleteTuples failed" }
            Err(AuthorizationError.WriteFailed(e))
        } catch (e: FgaValidationError) {
            logger.error(e) { "OpenFGA deleteTuples failed" }
            Err(AuthorizationError.WriteFailed(e))
        }
    }

    override suspend fun deleteAllTuplesFor(resource: AuthorizationResource): Result<Unit, AuthorizationError> {
        return try {
            // Phase 1: structural links pointing AT the resource (its children's parent links) —
            // the large, possibly multi-chunk set, and none of it grants anyone access to the
            // resource itself.
            resource.structuralReadQueries().forEach { deleteKeysChunked(readAllKeys(it)) }
            // Phase 2: relations held directly ON the resource (owner/editor/viewer) — reached only
            // if phase 1 fully succeeded, so a partial failure never strips the caller's own grant
            // before its dependents are cleaned up. See [AuthorizationService.deleteAllTuplesFor].
            deleteKeysChunked(readAllKeys(resource.directReadQuery()))
            Ok(Unit)
        } catch (e: ApiException) {
            logger.error(e) { "OpenFGA deleteAllTuplesFor failed for ${resource.toFgaObject()}" }
            Err(AuthorizationError.WriteFailed(e))
        } catch (e: FgaInvalidParameterException) {
            logger.error(e) { "OpenFGA deleteAllTuplesFor failed for ${resource.toFgaObject()}" }
            Err(AuthorizationError.WriteFailed(e))
        } catch (e: FgaValidationError) {
            logger.error(e) { "OpenFGA deleteAllTuplesFor failed for ${resource.toFgaObject()}" }
            Err(AuthorizationError.WriteFailed(e))
        }
    }

    /**
     * Reads every tuple matching [query], following continuation tokens, and maps each to a delete
     * key. Throws the OpenFGA SDK exceptions; [deleteAllTuplesFor] maps them to [AuthorizationError].
     */
    private suspend fun readAllKeys(query: ReadQuery): List<ClientTupleKeyWithoutCondition> {
        val request = ClientReadRequest()._object(query.objectRef)
        query.user?.let { request.user(it) }
        query.relation?.let { request.relation(it) }
        val keys = mutableListOf<ClientTupleKeyWithoutCondition>()
        var token: String? = null
        do {
            val options = ClientReadOptions().apply { token?.let { continuationToken(it) } }
            val response = client.read(request, options).await()
            response.tuples.forEach { tuple ->
                val key = tuple.key
                keys.add(
                    ClientTupleKeyWithoutCondition()
                        .user(key.user)
                        .relation(key.relation)
                        ._object(key.getObject()),
                )
            }
            token = response.continuationToken.takeIf { it.isNotBlank() }
        } while (token != null)
        return keys
    }

    /**
     * Deletes [keys] non-transactionally in chunks of [MAX_TUPLES_PER_WRITE] (OpenFGA caps a single
     * Write at that many tuples), ignoring already-missing tuples so a retry after a partial failure
     * converges. Throws the OpenFGA SDK exceptions; the caller maps them to [AuthorizationError].
     */
    private suspend fun deleteKeysChunked(keys: List<ClientTupleKeyWithoutCondition>) {
        if (keys.isEmpty()) return
        val options = ClientWriteOptions()
            .authorizationModelId(authorizationModelId)
            .disableTransactions(true)
            .transactionChunkSize(MAX_TUPLES_PER_WRITE)
            .onMissing(WriteRequestDeletes.OnMissingEnum.IGNORE)
        client.write(ClientWriteRequest().deletes(keys), options).await()
    }

}

/** A single OpenFGA Read filter — an object (full or type-only) with optional user/relation. */
private data class ReadQuery(val objectRef: String, val user: String? = null, val relation: String? = null)

/** Read queries for structural links that point AT this resource (from other resources). */
private fun AuthorizationResource.structuralReadQueries(): List<ReadQuery> = when (this) {
    is AuthorizationResource.TodoList ->
        listOf(ReadQuery(objectRef = "todo:", user = toFgaObject(), relation = "parent_list"))
    is AuthorizationResource.Todo -> emptyList()
}

/** Read query for the relations held directly ON this resource. */
private fun AuthorizationResource.directReadQuery(): ReadQuery = ReadQuery(objectRef = toFgaObject())

private fun AuthorizationResourceType.toFgaType(): String = when (this) {
    AuthorizationResourceType.TodoList -> "todo_list"
    AuthorizationResourceType.Todo -> "todo"
}

private fun AuthorizationResource.toFgaObject(): String = "${type.toFgaType()}:$id"

// ResourceRelation direction is not symmetric with UserRelation: `parent_list` is a relation
// defined ON `type todo` (see fga/authorization-model.fga), pointing to a `todo_list` — so the
// FGA object is the child (todo) and the FGA user is the parent (todo_list), i.e.
// `todo:<child>#parent_list@todo_list:<parent>`. Swapping these produces a tuple the schema
// rejects (this was a real bug once already — see AuthorizationError.WriteFailed handling).
private fun AuthorizationTuple.toClientTupleKey(): ClientTupleKey = when (this) {
    is AuthorizationTuple.UserRelation -> ClientTupleKey()
        .user("user:$userId")
        .relation(relation)
        ._object(resource.toFgaObject())
    is AuthorizationTuple.ResourceRelation -> ClientTupleKey()
        .user(parent.toFgaObject())
        .relation(relation)
        ._object(child.toFgaObject())
}

private fun AuthorizationTuple.toClientTupleKeyWithoutCondition(): ClientTupleKeyWithoutCondition = when (this) {
    is AuthorizationTuple.UserRelation -> ClientTupleKeyWithoutCondition()
        .user("user:$userId")
        .relation(relation)
        ._object(resource.toFgaObject())
    is AuthorizationTuple.ResourceRelation -> ClientTupleKeyWithoutCondition()
        .user(parent.toFgaObject())
        .relation(relation)
        ._object(child.toFgaObject())
}
