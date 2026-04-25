package com.example.todo.validation

import com.example.core.validation.ValidationErrors
import com.example.core.validation.toResult
import com.example.todo.domain.TodoForCreate
import com.example.todo.domain.TodoForUpdate
import com.github.michaelbull.result.Result
import io.konform.validation.Validation
import io.konform.validation.ValidationBuilder
import io.konform.validation.constraints.maxLength
import io.konform.validation.constraints.minLength

private const val TODO_NAME_MAX_LENGTH = 255

private fun ValidationBuilder<String>.todoName() {
    minLength(1)
    maxLength(TODO_NAME_MAX_LENGTH)
}

private val todoForCreateValidator = Validation {
    TodoForCreate::name { todoName() }
}

private val todoForUpdateValidator = Validation {
    TodoForUpdate::name { todoName() }
}

fun TodoForCreate.validate(): Result<TodoForCreate, ValidationErrors> =
    todoForCreateValidator(this).toResult()

fun TodoForUpdate.validate(): Result<TodoForUpdate, ValidationErrors> =
    todoForUpdateValidator(this).toResult()
