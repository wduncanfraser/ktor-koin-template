package com.example.config

import com.example.todo.api.TodoController
import com.example.generated.api.controllers.TodosController.Companion.todosRoutes
import io.ktor.server.routing.Routing
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject
import kotlin.getValue

fun Routing.apiRoutes() {
    val todoController by inject<TodoController>()

    route("/api/v1") {
        todosRoutes(todoController)
    }
}
