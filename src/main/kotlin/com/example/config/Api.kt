package com.example.config

import com.example.generated.api.controllers.TodoListsController.Companion.todoListsRoutes
import com.example.generated.api.controllers.TodoListsTodosController.Companion.todoListsTodosRoutes
import com.example.todolist.api.TodoListController
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.apiRoutes() {
    val todoListController by inject<TodoListController>()

    authenticate("auth-session") {
        route("/api/v1") {
            todoListsRoutes(todoListController)
            todoListsTodosRoutes(todoListController)
        }
    }
}
