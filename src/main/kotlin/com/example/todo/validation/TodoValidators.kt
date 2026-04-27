package com.example.todo.validation

import com.example.core.validation.ValidationErrors
import com.example.core.validation.itemName
import com.example.core.validation.toResult
import com.example.todo.domain.TodoForCreate
import com.example.todo.domain.TodoForUpdate
import com.github.michaelbull.result.Result
import io.konform.validation.Validation

private val todoForCreateValidator = Validation {
    TodoForCreate::name { itemName() }
}

private val todoForUpdateValidator = Validation {
    TodoForUpdate::name { itemName() }
}

fun TodoForCreate.validate(): Result<TodoForCreate, ValidationErrors> =
    todoForCreateValidator(this).toResult()

fun TodoForUpdate.validate(): Result<TodoForUpdate, ValidationErrors> =
    todoForUpdateValidator(this).toResult()
