package com.example.todo.repository.mappers

import com.example.generated.db.tables.records.TodoRecord
import com.example.todo.domain.TodoForSave
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant as JavaInstant
import java.util.UUID
import kotlin.time.Instant as KotlinInstant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

class TodoMapperTest : FunSpec({

    val fixedInstant: JavaInstant = JavaInstant.parse("2025-01-15T10:30:00Z")

    test("toDomain maps all fields from TodoRecord") {
        val id = UUID.randomUUID()
        val record = TodoRecord(
            id = id,
            name = "Buy groceries",
            completedAt = null,
            userId = "test-user",
            createdAt = fixedInstant,
            updatedAt = fixedInstant,
        )

        val result = TodoMapper.toDomain(record)

        assertSoftly(result) {
            this.id shouldBe id
            name shouldBe "Buy groceries"
            completedAt shouldBe null
            userId shouldBe "test-user"
            createdAt shouldBe fixedInstant.toKotlinInstant()
            updatedAt shouldBe fixedInstant.toKotlinInstant()
        }
    }

    test("toDomain maps completedAt when present") {
        val completedAt = JavaInstant.parse("2025-01-16T12:00:00Z")
        val record = TodoRecord(
            id = UUID.randomUUID(),
            name = "Done task",
            completedAt = completedAt,
            userId = "test-user",
            createdAt = fixedInstant,
            updatedAt = fixedInstant,
        )

        val result = TodoMapper.toDomain(record)

        result.completedAt shouldBe completedAt.toKotlinInstant()
    }

    test("toRecord maps all fields from TodoForSave") {
        val id = UUID.randomUUID()
        val kotlinInstant: KotlinInstant = fixedInstant.toKotlinInstant()
        val todo = TodoForSave(
            id = id,
            name = "Write tests",
            completedAt = kotlinInstant,
            userId = "test-user",
        )

        val result = TodoMapper.toRecord(todo)

        assertSoftly(result) {
            this.id shouldBe id
            name shouldBe "Write tests"
            completedAt shouldBe kotlinInstant.toJavaInstant()
            userId shouldBe "test-user"
        }
    }

    test("toRecord maps null completedAt") {
        val todo = TodoForSave(
            id = UUID.randomUUID(),
            name = "Incomplete task",
            completedAt = null,
            userId = "test-user",
        )

        val result = TodoMapper.toRecord(todo)

        result.completedAt shouldBe null
    }
})
