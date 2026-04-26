package com.example.todolist

import com.example.todolist.api.TodoListController
import com.example.todolist.repository.TodoListRepository
import com.example.todolist.services.TodoListService
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val todoListModule = module {
    singleOf(::TodoListRepository)
    singleOf(::TodoListService)
    singleOf(::TodoListController)
}
