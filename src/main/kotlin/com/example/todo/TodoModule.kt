package com.example.todo

import com.example.todo.api.TodoController
import com.example.todo.repository.TodoRepository
import com.example.todo.services.TodoService
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val todoModule = module {
    singleOf(::TodoRepository)
    singleOf(::TodoService)
    singleOf(::TodoController)
}
