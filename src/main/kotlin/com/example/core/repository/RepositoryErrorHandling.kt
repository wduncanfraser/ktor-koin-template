package com.example.core.repository

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.recoverIf
import com.github.michaelbull.result.runCatching
import com.github.michaelbull.result.toErrorIfNull
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jooq.exception.IntegrityConstraintViolationException

val logger = KotlinLogging.logger {}

/**
 * Type alias for [Result] which specializes it to our [RepositoryError] type.
 */
typealias RepositoryResult<V> = Result<V, RepositoryError>

/**
 * Helper function to run DB queries inside, and wrap any thrown exceptions into a [Result].
 */
inline fun <V> runWrappingError(block: () -> V): RepositoryResult<V> = runCatching(block).mapError {
    // DEBUG log exception messages to help track down swallowed exceptions
    logger.debug { "runWrappingError: $it" }
    when (it) {
        is IntegrityConstraintViolationException -> RepositoryError.RecordConstraintViolation(it)
        else -> RepositoryError.UnhandledException(it)
    }
}

/**
 * Helper function for when expecting a row count from a query of 1 (For example, running an update/delete query).
 * Returns [Unit] if 1.
 * If more than 1, returns [RepositoryError.UnhandledException].
 * Otherwise [RepositoryError.RecordNotFound].
 */
fun Result<Int, RepositoryError>.mapExpectingOne(): RepositoryResult<Unit> =
    andThen {
        if (it == 0) {
            Err(RepositoryError.RecordNotFound)
        } else if (it > 1) {
            Err(
                RepositoryError.UnhandledException(
                    RuntimeException("mapExpectingOne was expecting 0 or 1 rows, but received: $it"),
                ),
            )
        } else {
            Ok(Unit)
        }
    }

/**
 * Helper function that transforms a nullable query result to a concrete query result.
 * Returns [RepositoryError.RecordNotFound] if null.
 */
fun <V> Result<V?, RepositoryError>.toNotFoundIfNull(): RepositoryResult<V> =
    toErrorIfNull { RepositoryError.RecordNotFound }

/**
 * Helper function that transforms a non-nullable query result to a nullable query result.
 * Returns null if [RepositoryError.RecordNotFound].
 */
fun <V> Result<V, RepositoryError>.toNullIfNotFound(): RepositoryResult<V?> =
    recoverIf(
        { it is RepositoryError.RecordNotFound },
        { null },
    )

/**
 * Helper function that ignores [RepositoryError.RecordNotFound] on a non-nullable Unit result.
 */
fun RepositoryResult<Unit>.ignoreNotFound(): RepositoryResult<Unit> {
    return this.recoverIf(
        { it is RepositoryError.RecordNotFound },
        { },
    ).map { }
}
