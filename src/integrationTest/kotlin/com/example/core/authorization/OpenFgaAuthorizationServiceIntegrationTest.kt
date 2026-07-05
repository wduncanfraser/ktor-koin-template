package com.example.core.authorization

import com.example.IntegrationTestBase
import com.github.michaelbull.result.getError
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.koin.ktor.ext.get
import java.util.UUID

class OpenFgaAuthorizationServiceIntegrationTest : IntegrationTestBase({
    test("writeTuples surfaces an invalid relation as WriteFailed, not an uncaught exception") {
        val authorizationService = application.get<AuthorizationService>()

        // "owner" is a real relation in fga/authorization-model.fga, but only defined on
        // todo_list — not on todo — so the real OpenFGA server rejects this as invalid rather
        // than accepting the write, exercising the exception-handling path rather than a normal
        // success/failure result.
        val result = authorizationService.writeTuples(
            listOf(
                AuthorizationTuple.UserRelation(
                    userId = "test-user-id",
                    relation = "owner",
                    resource = AuthorizationResource.Todo(id = UUID.randomUUID(), todoListId = UUID.randomUUID()),
                ),
            ),
        )

        result.isErr shouldBe true
        result.getError().shouldBeInstanceOf<AuthorizationError.WriteFailed>()
    }
})
