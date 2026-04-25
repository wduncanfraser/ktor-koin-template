package com.example.todo.validation

import com.example.todo.domain.TodoForCreate
import com.example.todo.domain.TodoForUpdate
import com.github.michaelbull.result.onErr
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

class TodoValidatorsTest : FunSpec({
    context("TodoForCreate.validate()") {
        test("valid name passes") {
            TodoForCreate(name = "Cook dinner").validate().isOk shouldBe true
        }

        test("empty name fails") {
            TodoForCreate(name = "").validate().isErr shouldBe true
        }

        test("name at max length passes") {
            TodoForCreate(name = "a".repeat(255)).validate().isOk shouldBe true
        }

        test("name exceeding max length fails") {
            TodoForCreate(name = "a".repeat(256)).validate().isErr shouldBe true
        }

        test("validation error includes field path") {
            val result = TodoForCreate(name = "").validate()
            result.isErr shouldBe true
            var pathFound = false
            result.onErr { errors -> pathFound = errors.any { it.path.contains("name") } }
            pathFound shouldBe true
        }
    }

    context("TodoForUpdate.validate()") {
        val id = UUID.randomUUID()

        test("valid name passes") {
            TodoForUpdate(id = id, name = "Updated name", completed = false).validate().isOk shouldBe true
        }

        test("empty name fails") {
            TodoForUpdate(id = id, name = "", completed = false).validate().isErr shouldBe true
        }

        test("name exceeding max length fails") {
            TodoForUpdate(id = id, name = "a".repeat(256), completed = false).validate().isErr shouldBe true
        }
    }
})
