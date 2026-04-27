package com.example

import com.example.core.repository.RepositoryError
import com.example.todolist.domain.TodoListForSave
import com.example.todolist.repository.TodoListRepository
import com.github.michaelbull.result.getError
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.jooq.DSLContext
import org.koin.ktor.ext.get
import java.time.Duration
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

class RepositoryPatternsIntegrationTest : IntegrationTestBase({
    test("getById successfully acquires lock after previous transaction completes") {
        val ctx = application.get<DSLContext>()
        val repository = application.get<TodoListRepository>()
        val id = UUID.randomUUID()

        ctx.resultTransactionCoroutine { c ->
            repository.upsert(
                c = c,
                todoList = TodoListForSave(
                    id = id,
                    name = "Lock test",
                    description = null,
                    createdByUserId = "test-user",
                ),
            )
        }

        val lockAcquired = CompletableDeferred<Unit>()
        val releaseLock = CompletableDeferred<Unit>()

        coroutineScope {
            val lockHolder = async {
                ctx.resultTransactionCoroutine { config ->
                    val result = repository.getById(config.dsl(), "test-user", id, lockRecords = true)
                    lockAcquired.complete(Unit)
                    releaseLock.await()
                    result
                }
            }

            lockAcquired.await()

            // Start the second transaction while the lock is still held — it will block at the DB
            val secondResult = async {
                ctx.resultTransactionCoroutine { config ->
                    repository.getById(config.dsl(), "test-user", id, lockRecords = true)
                }
            }

            // Give the second transaction time to issue its SELECT FOR UPDATE before releasing
            delay(100.milliseconds)
            releaseLock.complete(Unit)
            lockHolder.await()

            secondResult.await().isOk shouldBe true
        }
    }

    test("getById returns UnhandledException on lock wait timeout") {
        val ctx = application.get<DSLContext>()
        val repository = application.get<TodoListRepository>()
        val id = UUID.randomUUID()

        ctx.resultTransactionCoroutine { c ->
            repository.upsert(
                c = c,
                todoList = TodoListForSave(
                    id = id,
                    name = "Lock test",
                    description = null,
                    createdByUserId = "test-user",
                ),
            )
        }

        val lockAcquired = CompletableDeferred<Unit>()
        val releaseLock = CompletableDeferred<Unit>()

        coroutineScope {
            // Coroutine 1: hold a FOR UPDATE lock on the row
            val lockHolder = async {
                ctx.resultTransactionCoroutine { config ->
                    val result = repository.getById(config.dsl(), "test-user", id, lockRecords = true)
                    lockAcquired.complete(Unit)
                    releaseLock.await()
                    result
                }
            }

            // Wait until the lock is confirmed to be held
            lockAcquired.await()

            // Coroutine 2: attempt to acquire the same lock with a short timeout
            val result = ctx.resultTransactionCoroutine { config ->
                repository.getById(
                    ctx = config.dsl(),
                    createdByUserId = "test-user",
                    id = id,
                    lockRecords = true,
                    lockWait = Duration.ofMillis(100),
                )
            }

            releaseLock.complete(Unit)
            lockHolder.await()

            result.isErr shouldBe true
            result.getError().shouldBeInstanceOf<RepositoryError.LockTimeout>()
        }
    }
})
