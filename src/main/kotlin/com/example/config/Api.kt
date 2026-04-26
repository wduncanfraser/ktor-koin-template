package com.example.config

import com.example.generated.api.controllers.TodoListsController.Companion.todoListsRoutes
import com.example.generated.api.controllers.TodoListsTodosController.Companion.todoListsTodosRoutes
import com.example.generated.api.controllers.TodosController.Companion.todosRoutes
import com.example.todo.api.TodoController
import com.example.todolist.api.TodoListController
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.apiRoutes() {
    val todoController by inject<TodoController>()
    val todoListController by inject<TodoListController>()

    authenticate("auth-session") {
        route("/api/v1") {
            todosRoutes(todoController)
            todoListsRoutes(todoListController)
            todoListsTodosRoutes(todoListController)
        }
    }
}
