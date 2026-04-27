package com.example.todolist.api.mappers

import com.example.core.api.mappers.PaginationContractMapper
import com.example.core.domain.Page
import com.example.generated.api.models.CreateTodoListRequestContract
import com.example.generated.api.models.ListTodoListsResponseContract
import com.example.generated.api.models.TodoListResponseContract
import com.example.generated.api.models.UpdateTodoListRequestContract
import com.example.todolist.domain.TodoList
import com.example.todolist.domain.TodoListForCreate
import com.example.todolist.domain.TodoListForUpdate

/**
 * Contract mappers are used to convert from our generated contract models to Domain models, and vice versa.
 * DTO pattern.
 */
object TodoListContractMapper {
    fun toContract(todoListPage: Page<TodoList>) = ListTodoListsResponseContract(
        data = todoListPage.data.map(::toContract),
        pagination = PaginationContractMapper.toContract(todoListPage),
    )

    fun toContract(todoList: TodoList) = TodoListResponseContract(
        id = todoList.id.toString(),
        name = todoList.name,
        description = todoList.description,
        createdBy = todoList.createdByUserId,
        createdAt = todoList.createdAt,
        updatedAt = todoList.updatedAt,
    )

    fun toDomain(contract: CreateTodoListRequestContract) = TodoListForCreate(
        name = contract.name,
        description = contract.description,
    )

    fun toDomain(listId: String, contract: UpdateTodoListRequestContract) = TodoListForUpdate(
        id = TodoListIdMapper.toDomain(listId),
        name = contract.name,
        description = contract.description,
    )
}
