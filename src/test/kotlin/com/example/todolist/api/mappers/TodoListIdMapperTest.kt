package com.example.todolist.api.mappers

import com.example.core.api.exceptions.ProblemDetailsException
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import java.util.UUID

class TodoListIdMapperTest : FunSpec({

    context("toDomain") {
        test("converts valid UUID string") {
            val uuid = UUID.randomUUID()

            val result = TodoListIdMapper.toDomain(uuid.toString())

            result shouldBe uuid
        }

        test("throws ProblemDetailsException for invalid UUID") {
            val exception = shouldThrow<ProblemDetailsException> {
                TodoListIdMapper.toDomain("not-a-uuid")
            }

            assertSoftly(exception) {
                statusCode shouldBe HttpStatusCode.NotFound
                message shouldBe "Todo list not found: listId=not-a-uuid"
            }
        }
    }
})
