package com.example.todolist.api.mappers

import com.example.core.domain.Page
import com.example.generated.api.models.CreateTodoListRequestContract
import com.example.generated.api.models.UpdateTodoListRequestContract
import com.example.todolist.domain.TodoList
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.util.UUID
import kotlin.time.Instant

class TodoListContractMapperTest : FunSpec({

    val fixedInstant = Instant.parse("2025-01-15T10:30:00Z")

    context("toContract") {
        test("maps TodoList") {
            val id = UUID.randomUUID()
            val todoList = TodoList(
                id = id,
                name = "Shopping",
                description = "Weekly groceries",
                createdByUserId = "test-user",
                createdAt = fixedInstant,
                updatedAt = fixedInstant,
            )

            val result = TodoListContractMapper.toContract(todoList)

            assertSoftly(result) {
                this.id shouldBe id.toString()
                name shouldBe "Shopping"
                description shouldBe "Weekly groceries"
                createdBy shouldBe "test-user"
                createdAt shouldBe fixedInstant
                updatedAt shouldBe fixedInstant
            }
        }

        test("maps null description") {
            val todoList = TodoList(
                id = UUID.randomUUID(),
                name = "Shopping",
                description = null,
                createdByUserId = "test-user",
                createdAt = fixedInstant,
                updatedAt = fixedInstant,
            )

            val result = TodoListContractMapper.toContract(todoList)

            result.description shouldBe null
        }

        test("maps Page of TodoLists") {
            val list1 = TodoList(
                id = UUID.randomUUID(),
                name = "First",
                description = null,
                createdByUserId = "test-user",
                createdAt = fixedInstant,
                updatedAt = fixedInstant,
            )
            val list2 = TodoList(
                id = UUID.randomUUID(),
                name = "Second",
                description = "A description",
                createdByUserId = "test-user",
                createdAt = fixedInstant,
                updatedAt = fixedInstant,
            )
            val page = Page(
                data = listOf(list1, list2),
                pageNumber = 1,
                pageSize = 20,
                totalRows = 2,
                totalPages = 1,
            )

            val result = TodoListContractMapper.toContract(page)

            assertSoftly(result) {
                data shouldHaveSize 2
                data[0].name shouldBe "First"
                data[1].name shouldBe "Second"
                pagination.page shouldBe 1
                pagination.pageSize shouldBe 20
                pagination.totalRows shouldBe 2
                pagination.totalPages shouldBe 1
            }
        }
    }

    context("toDomain") {
        test("maps CreateTodoListRequestContract") {
            val contract = CreateTodoListRequestContract(name = "Shopping", description = "Weekly groceries")

            val result = TodoListContractMapper.toDomain(contract)

            assertSoftly(result) {
                name shouldBe "Shopping"
                description shouldBe "Weekly groceries"
            }
        }

        test("maps CreateTodoListRequestContract with null description") {
            val contract = CreateTodoListRequestContract(name = "Shopping")

            val result = TodoListContractMapper.toDomain(contract)

            result.description shouldBe null
        }

        test("maps UpdateTodoListRequestContract") {
            val id = UUID.randomUUID()
            val contract = UpdateTodoListRequestContract(name = "Updated", description = "New description")

            val result = TodoListContractMapper.toDomain(id.toString(), contract)

            assertSoftly(result) {
                this.id shouldBe id
                name shouldBe "Updated"
                description shouldBe "New description"
            }
        }

        test("maps UpdateTodoListRequestContract with null description") {
            val id = UUID.randomUUID()
            val contract = UpdateTodoListRequestContract(name = "Updated", description = null)

            val result = TodoListContractMapper.toDomain(id.toString(), contract)

            result.description shouldBe null
        }
    }
})
