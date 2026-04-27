package com.example

import com.example.core.repository.RepositoryError
import com.example.todolist.domain.TodoListForSave
import com.example.todolist.repository.TodoListRepository
import com.github.michaelbull.result.Err
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import org.koin.ktor.ext.get
import java.util.UUID

class TransactionExtensionsIntegrationTest : IntegrationTestBase({
    test("resultTransactionCoroutine rolls back inserts when Err is returned") {
        val ctx = application.get<DSLContext>()
        val repository = application.get<TodoListRepository>()
        val id = UUID.randomUUID()

        val result = ctx.resultTransactionCoroutine { c ->
            repository.upsert(
                c = c,
                todoList = TodoListForSave(
                    id = id,
                    name = "Rollback test",
                    description = null,
                    createdByUserId = "test-user",
                ),
            )
            Err(RepositoryError.RecordNotFound)
        }

        result shouldBe Err(RepositoryError.RecordNotFound)

        val afterRollback = repository.getById(ctx, "test-user", id)
        afterRollback shouldBe Err(RepositoryError.RecordNotFound)
    }

    test("resultTransactionCoroutine rolls back inserts when an exception is thrown") {
        val ctx = application.get<DSLContext>()
        val repository = application.get<TodoListRepository>()
        val id = UUID.randomUUID()

        shouldThrow<RuntimeException> {
            ctx.resultTransactionCoroutine { c ->
                repository.upsert(
                    c,
                    TodoListForSave(id = id, name = "Rollback test", description = null, createdByUserId = "test-user"),
                )
                throw RuntimeException("Simulated failure")
            }
        }

        val afterRollback = repository.getById(ctx, "test-user", id)
        afterRollback shouldBe Err(RepositoryError.RecordNotFound)
    }

    test("resultTransactionCoroutine commits inserts when Ok is returned") {
        val ctx = application.get<DSLContext>()
        val repository = application.get<TodoListRepository>()
        val id = UUID.randomUUID()

        val result = ctx.resultTransactionCoroutine { c ->
            repository.upsert(
                c = c,
                todoList = TodoListForSave(
                    id = id,
                    name = "Commit test",
                    description = null,
                    createdByUserId = "test-user",
                ),
            )
        }

        result.isOk shouldBe true

        val afterCommit = repository.getById(ctx, "test-user", id)
        afterCommit.isOk shouldBe true
    }
})
