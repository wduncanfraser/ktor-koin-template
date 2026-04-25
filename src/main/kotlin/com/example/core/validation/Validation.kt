package com.example.core.validation

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.konform.validation.Invalid
import io.konform.validation.Valid
import io.konform.validation.ValidationResult

fun <T> ValidationResult<T>.toResult(): Result<T, ValidationErrors> = when (this) {
    is Valid -> Ok(value)
    is Invalid -> Err(errors.map { FieldValidationError(it.dataPath, it.message) })
}

fun ValidationErrors.format(): String = joinToString("; ") { "${it.path}: ${it.message}" }
