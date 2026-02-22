package com.example

import com.github.michaelbull.result.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.mono
import org.jooq.Configuration
import org.jooq.DSLContext

private class ResultRollbackException(val result: Any?) : Throwable()

suspend fun <T> DSLContext.suspendTransaction(
    block: suspend (DSLContext) -> T
): T {
    return transactionPublisher { trx ->
        mono(Dispatchers.Unconfined) {
            block(trx.dsl())
        }
    }.awaitSingle()
}

/**
 * Runs [transactional] inside a jOOQ R2DBC transaction, automatically rolling back if the returned
 * [Result] is an error.
 *
 * ### R2DBC rollback behaviour
 * With an R2DBC [org.jooq.DSLContext], jOOQ manages transactions via [DSLContext.transactionPublisher],
 * which commits on successful completion of the inner [org.reactivestreams.Publisher] and rolls back
 * when that publisher signals an error (`onError`). There is no direct coroutine equivalent of
 * `transactionCoroutine` that works with R2DBC.
 *
 * Rolling back explicitly via `dsl().rollback().awaitSingle()` is not viable because jOOQ's
 * R2DBC rollback publisher is void (it never emits `onNext`), so `awaitSingle()` throws
 * [NoSuchElementException].
 *
 * Instead, when [transactional] returns an [com.github.michaelbull.result.Err], a
 * [ResultRollbackException] carrying the result is thrown inside the `mono` block. This signals
 * `onError` to the reactive chain, causing [DSLContext.transactionPublisher] to issue the rollback. The
 * exception is then caught outside the publisher to return the original [com.github.michaelbull.result.Err]
 * value to the caller.
 */
suspend fun <T : Result<*, *>> DSLContext.resultTransactionCoroutine(
    transactional: suspend (Configuration) -> T,
): T {
    return try {
        transactionPublisher { trx ->
            mono(Dispatchers.Unconfined) {
                val result = transactional(trx)
                if (result.isErr) {
                    throw ResultRollbackException(result)
                }
                result
            }
        }.awaitSingle()
    } catch (e: ResultRollbackException) {
        @Suppress("UNCHECKED_CAST")
        e.result as T
    }
}
