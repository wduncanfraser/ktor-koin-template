package com.example.todo.api.mappers

import com.example.core.api.mappers.PaginationContractMapper
import com.example.core.models.Page
import com.example.generated.api.models.CreateTodoRequestContract
import com.example.generated.api.models.ListTodosResponseContract
import com.example.generated.api.models.TodoResponseContract
import com.example.generated.api.models.UpdateTodoRequestContract
import com.example.todo.domain.Todo
import com.example.todo.domain.TodoForCreate
import com.example.todo.domain.TodoForUpdate
import kotlinx.datetime.toDeprecatedInstant
import java.util.UUID

/**
 * Contract mappers are used to convert from our generated contract models to Domain models, and vice versa.
 * DTO pattern.
 */
object TodoContractMapper {
    fun toContract(todoPage: Page<Todo>) = ListTodosResponseContract(
        data = todoPage.data.map(::toContract),
        pagination = PaginationContractMapper.toContract(todoPage),
    )

    fun toContract(todo: Todo) = TodoResponseContract(
        id = todo.id.toString(),
        name = todo.name,
        completed = todo.completedAt != null,
        completedAt = todo.completedAt?.toDeprecatedInstant(),
        createdAt = todo.createdAt.toDeprecatedInstant(),
        updatedAt = todo.modifiedAt.toDeprecatedInstant(),
    )

    fun toDomain(contract: CreateTodoRequestContract): TodoForCreate {
        return TodoForCreate(contract.name)
    }

    fun toDomain(todoId: String, contract: UpdateTodoRequestContract): TodoForUpdate {
        return TodoForUpdate(
            id = UUID.fromString(todoId),
            name = contract.name,
            completed = contract.completed,
        )
    }
}
