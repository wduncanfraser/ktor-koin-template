package com.example.core.validation

data class FieldValidationError(val path: String, val message: String)

typealias ValidationErrors = List<FieldValidationError>
