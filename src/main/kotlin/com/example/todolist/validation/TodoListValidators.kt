package com.example.todolist.validation

import com.example.core.validation.ValidationErrors
import com.example.core.validation.itemName
import com.example.core.validation.toResult
import com.example.todolist.domain.TodoListForCreate
import com.example.todolist.domain.TodoListForUpdate
import com.github.michaelbull.result.Result
import io.konform.validation.Validation
import io.konform.validation.ValidationBuilder
import io.konform.validation.constraints.minLength

private fun ValidationBuilder<String>.nonEmptyDescription() {
    minLength(1)
}

private val todoListForCreateValidator = Validation {
    TodoListForCreate::name { itemName() }
    TodoListForCreate::description ifPresent { nonEmptyDescription() }
}

private val todoListForUpdateValidator = Validation {
    TodoListForUpdate::name { itemName() }
    TodoListForUpdate::description ifPresent { nonEmptyDescription() }
}

fun TodoListForCreate.validate(): Result<TodoListForCreate, ValidationErrors> =
    todoListForCreateValidator(this).toResult()

fun TodoListForUpdate.validate(): Result<TodoListForUpdate, ValidationErrors> =
    todoListForUpdateValidator(this).toResult()
