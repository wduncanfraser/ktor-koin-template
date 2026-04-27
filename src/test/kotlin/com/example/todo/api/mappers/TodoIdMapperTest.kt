package com.example.todo.api.mappers

import com.example.core.api.exceptions.ProblemDetailsException
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import java.util.UUID

class TodoIdMapperTest : FunSpec({

    context("toDomain") {
        test("converts valid UUID string") {
            val uuid = UUID.randomUUID()

            val result = TodoIdMapper.toDomain(uuid.toString())

            result shouldBe uuid
        }

        test("throws ProblemDetailsException for invalid UUID") {
            val exception = shouldThrow<ProblemDetailsException> {
                TodoIdMapper.toDomain("not-a-uuid")
            }

            assertSoftly(exception) {
                statusCode shouldBe HttpStatusCode.NotFound
                message shouldBe "Todo not found: todoId=not-a-uuid"
            }
        }
    }
})
