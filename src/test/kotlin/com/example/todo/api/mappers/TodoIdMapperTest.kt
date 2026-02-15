package com.example.todo.api.mappers

import com.example.core.api.exceptions.ProblemDetailsException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import java.util.UUID

class TodoIdMapperTest : FunSpec({

    test("toDomain converts valid UUID string") {
        val uuid = UUID.randomUUID()

        val result = TodoIdMapper.toDomain(uuid.toString())

        result shouldBe uuid
    }

    test("toDomain throws ProblemDetailsException for invalid UUID") {
        val exception = shouldThrow<ProblemDetailsException> {
            TodoIdMapper.toDomain("not-a-uuid")
        }

        exception.statusCode shouldBe HttpStatusCode.NotFound
        exception.message shouldBe "Todo not found: todoId=not-a-uuid"
    }
})
