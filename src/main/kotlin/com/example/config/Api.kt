package com.example.config

import com.example.generated.api.controllers.TodosController.Companion.todosRoutes
import com.example.todo.api.TodoController
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.apiRoutes() {
    val todoController by inject<TodoController>()

    route("/api/v1") {
        todosRoutes(todoController)
    }
}
