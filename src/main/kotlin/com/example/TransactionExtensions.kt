package com.example

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import kotlinx.coroutines.reactive.awaitSingle
import org.jooq.Configuration
import org.jooq.DSLContext
import org.jooq.kotlin.coroutines.transactionCoroutine
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

suspend fun <T : Result<*, *>> DSLContext.resultTransactionCoroutine(
    transactional: suspend (Configuration) -> T,
): T = this.resultTransactionCoroutine(EmptyCoroutineContext, transactional)

suspend fun <T : Result<*, *>> DSLContext.resultTransactionCoroutine(
    context: CoroutineContext,
    transactional: suspend (Configuration) -> T,
): T = this.transactionCoroutine(context) { c ->
    val txDsl = c.dsl()
    val result = transactional(c)
    result.onFailure {
        txDsl.rollback().awaitSingle()
    }
    result
}
