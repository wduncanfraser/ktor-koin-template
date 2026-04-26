package com.example.todolist.validation

import com.example.todolist.domain.TodoListForCreate
import com.example.todolist.domain.TodoListForUpdate
import com.github.michaelbull.result.onErr
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

class TodoListValidatorsTest : FunSpec({
    context("TodoListForCreate.validate()") {
        test("valid name passes") {
            TodoListForCreate(name = "Shopping", description = null).validate().isOk shouldBe true
        }

        test("empty name fails") {
            TodoListForCreate(name = "", description = null).validate().isErr shouldBe true
        }

        test("name at max length passes") {
            TodoListForCreate(name = "a".repeat(255), description = null).validate().isOk shouldBe true
        }

        test("name exceeding max length fails") {
            TodoListForCreate(name = "a".repeat(256), description = null).validate().isErr shouldBe true
        }

        test("null description passes") {
            TodoListForCreate(name = "Shopping", description = null).validate().isOk shouldBe true
        }

        test("non-empty description passes") {
            TodoListForCreate(name = "Shopping", description = "Weekly groceries").validate().isOk shouldBe true
        }

        test("empty description fails") {
            TodoListForCreate(name = "Shopping", description = "").validate().isErr shouldBe true
        }

        test("validation error includes field path for name") {
            val result = TodoListForCreate(name = "", description = null).validate()
            result.isErr shouldBe true
            var pathFound = false
            result.onErr { errors -> pathFound = errors.any { it.path.contains("name") } }
            pathFound shouldBe true
        }

        test("validation error includes field path for description") {
            val result = TodoListForCreate(name = "Shopping", description = "").validate()
            result.isErr shouldBe true
            var pathFound = false
            result.onErr { errors -> pathFound = errors.any { it.path.contains("description") } }
            pathFound shouldBe true
        }
    }

    context("TodoListForUpdate.validate()") {
        val id = UUID.randomUUID()

        test("valid name passes") {
            TodoListForUpdate(id = id, name = "Shopping", description = null).validate().isOk shouldBe true
        }

        test("empty name fails") {
            TodoListForUpdate(id = id, name = "", description = null).validate().isErr shouldBe true
        }

        test("name exceeding max length fails") {
            TodoListForUpdate(id = id, name = "a".repeat(256), description = null).validate().isErr shouldBe true
        }

        test("null description passes") {
            TodoListForUpdate(id = id, name = "Shopping", description = null).validate().isOk shouldBe true
        }

        test("empty description fails") {
            TodoListForUpdate(id = id, name = "Shopping", description = "").validate().isErr shouldBe true
        }
    }
})
