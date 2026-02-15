package com.example.todo.api.mappers

import com.example.core.domain.Page
import com.example.generated.api.models.CreateTodoRequestContract
import com.example.generated.api.models.UpdateTodoRequestContract
import com.example.todo.domain.Todo
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.util.UUID
import kotlin.time.Instant

class TodoContractMapperTest : FunSpec({

    val fixedInstant = Instant.parse("2025-01-15T10:30:00Z")
    val completedInstant = Instant.parse("2025-01-16T12:00:00Z")

    test("toContract maps Todo with null completedAt") {
        val id = UUID.randomUUID()
        val todo = Todo(
            id = id,
            name = "Incomplete task",
            completedAt = null,
            modifiedAt = fixedInstant,
            createdAt = fixedInstant,
        )

        val result = TodoContractMapper.toContract(todo)

        result.id shouldBe id.toString()
        result.name shouldBe "Incomplete task"
        result.completed shouldBe false
        result.completedAt shouldBe null
        result.createdAt shouldBe fixedInstant
        result.updatedAt shouldBe fixedInstant
    }

    test("toContract maps Todo with completedAt") {
        val todo = Todo(
            id = UUID.randomUUID(),
            name = "Done task",
            completedAt = completedInstant,
            modifiedAt = fixedInstant,
            createdAt = fixedInstant,
        )

        val result = TodoContractMapper.toContract(todo)

        result.completed shouldBe true
        result.completedAt shouldBe completedInstant
    }

    test("toContract maps Page of Todos") {
        val todo1 = Todo(
            id = UUID.randomUUID(),
            name = "First",
            completedAt = null,
            modifiedAt = fixedInstant,
            createdAt = fixedInstant,
        )
        val todo2 = Todo(
            id = UUID.randomUUID(),
            name = "Second",
            completedAt = completedInstant,
            modifiedAt = fixedInstant,
            createdAt = fixedInstant,
        )
        val page = Page(
            data = listOf(todo1, todo2),
            pageNumber = 1,
            pageSize = 20,
            totalRows = 2,
            totalPages = 1,
        )

        val result = TodoContractMapper.toContract(page)

        result.data shouldHaveSize 2
        result.data[0].name shouldBe "First"
        result.data[1].name shouldBe "Second"
        result.pagination.page shouldBe 1
        result.pagination.pageSize shouldBe 20
        result.pagination.totalRows shouldBe 2
        result.pagination.totalPages shouldBe 1
    }

    test("toDomain maps CreateTodoRequestContract") {
        val contract = CreateTodoRequestContract(name = "New todo")

        val result = TodoContractMapper.toDomain(contract)

        result.name shouldBe "New todo"
    }

    test("toDomain maps UpdateTodoRequestContract") {
        val id = UUID.randomUUID()
        val contract = UpdateTodoRequestContract(name = "Updated", completed = true)

        val result = TodoContractMapper.toDomain(id.toString(), contract)

        result.id shouldBe id
        result.name shouldBe "Updated"
        result.completed shouldBe true
    }
})
