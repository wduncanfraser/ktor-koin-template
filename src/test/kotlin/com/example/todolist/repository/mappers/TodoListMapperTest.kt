package com.example.todolist.repository.mappers

import com.example.generated.db.tables.records.TodoListRecord
import com.example.todolist.domain.TodoListForSave
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant as JavaInstant
import java.util.UUID
import kotlin.time.toKotlinInstant

class TodoListMapperTest : FunSpec({

    val fixedInstant: JavaInstant = JavaInstant.parse("2025-01-15T10:30:00Z")

    context("toDomain") {
        test("maps all fields from TodoListRecord") {
            val id = UUID.randomUUID()
            val record = TodoListRecord(
                id = id,
                name = "Shopping",
                description = "Weekly groceries",
                createdByUserId = "test-user",
                createdAt = fixedInstant,
                updatedAt = fixedInstant,
            )

            val result = TodoListMapper.toDomain(record)

            assertSoftly(result) {
                this.id shouldBe id
                name shouldBe "Shopping"
                description shouldBe "Weekly groceries"
                createdByUserId shouldBe "test-user"
                createdAt shouldBe fixedInstant.toKotlinInstant()
                updatedAt shouldBe fixedInstant.toKotlinInstant()
            }
        }

        test("maps null description") {
            val record = TodoListRecord(
                id = UUID.randomUUID(),
                name = "Shopping",
                description = null,
                createdByUserId = "test-user",
                createdAt = fixedInstant,
                updatedAt = fixedInstant,
            )

            val result = TodoListMapper.toDomain(record)

            result.description shouldBe null
        }
    }

    context("toRecord") {
        test("maps all fields from TodoListForSave") {
            val id = UUID.randomUUID()
            val todoList = TodoListForSave(
                id = id,
                name = "Shopping",
                description = "Weekly groceries",
                createdByUserId = "test-user",
            )

            val result = TodoListMapper.toRecord(todoList)

            assertSoftly(result) {
                this.id shouldBe id
                name shouldBe "Shopping"
                description shouldBe "Weekly groceries"
                createdByUserId shouldBe "test-user"
            }
        }

        test("maps null description") {
            val todoList = TodoListForSave(
                id = UUID.randomUUID(),
                name = "Shopping",
                description = null,
                createdByUserId = "test-user",
            )

            val result = TodoListMapper.toRecord(todoList)

            result.description shouldBe null
        }
    }
})
