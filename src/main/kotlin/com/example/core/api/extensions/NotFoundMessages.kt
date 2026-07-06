package com.example.core.api.extensions

import java.util.UUID

/*
 * Shared 404 message bodies for lookups, used by Controllers and ID mappers
 */
fun todoNotFoundMessage(todoId: String): String = "Todo not found: todoId=$todoId"
fun todoNotFoundMessage(todoId: UUID): String = todoNotFoundMessage(todoId.toString())
fun todoListNotFoundMessage(listId: String): String = "Todo list not found: listId=$listId"
fun todoListNotFoundMessage(listId: UUID): String = todoListNotFoundMessage(listId.toString())
