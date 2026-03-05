package com.example

import com.example.core.repository.RepositoryError
import com.example.todo.domain.TodoForSave
import com.example.todo.repository.TodoRepository
import com.github.michaelbull.result.Err
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import org.koin.ktor.ext.get
import java.util.UUID

class TransactionExtensionsIntegrationTest : IntegrationTestBase({
    test("resultTransactionCoroutine rolls back inserts when Err is returned") {
        val ctx = application.get<DSLContext>()
        val repository = application.get<TodoRepository>()
        val id = UUID.randomUUID()

        val result = ctx.resultTransactionCoroutine { c ->
            repository.upsert(c, TodoForSave(id = id, name = "Rollback test", completedAt = null, userId = "test-user"))
            Err(RepositoryError.RecordNotFound)
        }

        result shouldBe Err(RepositoryError.RecordNotFound)

        val afterRollback = repository.getById(ctx, "test-user", id)
        afterRollback shouldBe Err(RepositoryError.RecordNotFound)
    }

    test("resultTransactionCoroutine rolls back inserts when an exception is thrown") {
        val ctx = application.get<DSLContext>()
        val repository = application.get<TodoRepository>()
        val id = UUID.randomUUID()

        shouldThrow<RuntimeException> {
            ctx.resultTransactionCoroutine { c ->
                val todo = TodoForSave(id = id, name = "Rollback test", completedAt = null, userId = "test-user")
                repository.upsert(c, todo)
                throw RuntimeException("Simulated failure")
            }
        }

        val afterRollback = repository.getById(ctx, "test-user", id)
        afterRollback shouldBe Err(RepositoryError.RecordNotFound)
    }

    test("resultTransactionCoroutine commits inserts when Ok is returned") {
        val ctx = application.get<DSLContext>()
        val repository = application.get<TodoRepository>()
        val id = UUID.randomUUID()

        val result = ctx.resultTransactionCoroutine { c ->
            repository.upsert(c, TodoForSave(id = id, name = "Commit test", completedAt = null, userId = "test-user"))
        }

        result.isOk shouldBe true

        val afterCommit = repository.getById(ctx, "test-user", id)
        afterCommit.isOk shouldBe true
    }
})
