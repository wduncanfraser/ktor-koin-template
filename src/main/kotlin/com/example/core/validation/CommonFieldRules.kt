package com.example.core.validation

import io.konform.validation.ValidationBuilder
import io.konform.validation.constraints.maxLength
import io.konform.validation.constraints.minLength

const val ITEM_NAME_MAX_LENGTH = 255

fun ValidationBuilder<String>.itemName() {
    minLength(1)
    maxLength(ITEM_NAME_MAX_LENGTH)
}
