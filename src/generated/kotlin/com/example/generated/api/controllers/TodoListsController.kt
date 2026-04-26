package com.example.generated.api.controllers

import com.example.generated.api.models.CreateTodoListRequestContract
import com.example.generated.api.models.ListTodoListsResponseContract
import com.example.generated.api.models.TodoListResponseContract
import com.example.generated.api.models.UpdateTodoListRequestContract
import io.ktor.http.Headers
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.MissingRequestParameterException
import io.ktor.server.plugins.ParameterConversionException
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.`get`
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.util.converters.ConversionService
import io.ktor.util.converters.DefaultConversionService
import io.ktor.util.reflect.typeInfo
import kotlin.Any
import kotlin.Int
import kotlin.String

public interface TodoListsController {
  /**
   * List all todo lists
   *
   * Route is expected to respond with
   * [com.example.generated.api.models.ListTodoListsResponseContract].
   * Use [com.example.generated.api.controllers.TypedApplicationCall.respondTyped] to send the
   * response.
   *
   * @param pageSize The number of items to return in a single request.
   * @param page The page number of results to return.
   * @param call Decorated ApplicationCall with additional typed respond methods
   */
  public suspend fun listTodoLists(
    pageSize: Int?,
    page: Int?,
    call: TypedApplicationCall<ListTodoListsResponseContract>,
  )

  /**
   * Create a todo list
   *
   * Route is expected to respond with [com.example.generated.api.models.TodoListResponseContract].
   * Use [com.example.generated.api.controllers.TypedApplicationCall.respondTyped] to send the
   * response.
   *
   * @param createTodoListRequest The request body for creating a Todo list.
   * @param call Decorated ApplicationCall with additional typed respond methods
   */
  public suspend fun createTodoList(createTodoListRequest: CreateTodoListRequestContract,
      call: TypedApplicationCall<TodoListResponseContract>)

  /**
   * Get a todo list
   *
   * Route is expected to respond with [com.example.generated.api.models.TodoListResponseContract].
   * Use [com.example.generated.api.controllers.TypedApplicationCall.respondTyped] to send the
   * response.
   *
   * @param listId The unique identifier of the Todo list.
   * @param call Decorated ApplicationCall with additional typed respond methods
   */
  public suspend fun getTodoList(listId: String,
      call: TypedApplicationCall<TodoListResponseContract>)

  /**
   * Update a todo list
   *
   * Route is expected to respond with [com.example.generated.api.models.TodoListResponseContract].
   * Use [com.example.generated.api.controllers.TypedApplicationCall.respondTyped] to send the
   * response.
   *
   * @param updateTodoListRequest The request body for updating a Todo list.
   * @param listId The unique identifier of the Todo list.
   * @param call Decorated ApplicationCall with additional typed respond methods
   */
  public suspend fun updateTodoList(
    listId: String,
    updateTodoListRequest: UpdateTodoListRequestContract,
    call: TypedApplicationCall<TodoListResponseContract>,
  )

  /**
   * Delete a todo list
   *
   * Route is expected to respond with status 204.
   * Use [io.ktor.server.response.respond] to send the response.
   *
   * @param listId The unique identifier of the Todo list.
   * @param call The Ktor application call
   */
  public suspend fun deleteTodoList(listId: String, call: ApplicationCall)

  public companion object {
    /**
     * Mounts all routes for the TodoLists resource
     *
     * - GET /todo-lists List all todo lists
     * - POST /todo-lists Create a todo list
     * - GET /todo-lists/{list-id} Get a todo list
     * - PUT /todo-lists/{list-id} Update a todo list
     * - DELETE /todo-lists/{list-id} Delete a todo list
     */
    public fun Route.todoListsRoutes(controller: TodoListsController) {
      `get`("/todo-lists") {
        val pageSize = call.request.queryParameters.getTyped<kotlin.Int>("pageSize")
        val page = call.request.queryParameters.getTyped<kotlin.Int>("page")
        controller.listTodoLists(pageSize, page, TypedApplicationCall(call))
      }
      post("/todo-lists") {
        val createTodoListRequest = call.receive<CreateTodoListRequestContract>()
        controller.createTodoList(createTodoListRequest, TypedApplicationCall(call))
      }
      `get`("/todo-lists/{list-id}") {
        val listId = call.parameters.getTypedOrFail<kotlin.String>("list-id")
        controller.getTodoList(listId, TypedApplicationCall(call))
      }
      put("/todo-lists/{list-id}") {
        val listId = call.parameters.getTypedOrFail<kotlin.String>("list-id")
        val updateTodoListRequest = call.receive<UpdateTodoListRequestContract>()
        controller.updateTodoList(listId, updateTodoListRequest, TypedApplicationCall(call))
      }
      delete("/todo-lists/{list-id}") {
        val listId = call.parameters.getTypedOrFail<kotlin.String>("list-id")
        controller.deleteTodoList(listId, call)
      }
    }

    /**
     * Gets parameter value associated with this name or null if the name is not present.
     * Converting to type R using ConversionService.
     *
     * Throws:
     *   ParameterConversionException - when conversion from String to R fails
     */
    private inline fun <reified R : Any> Parameters.getTyped(name: String,
        conversionService: ConversionService = DefaultConversionService): R? {
      val values = getAll(name) ?: return null
      val typeInfo = typeInfo<R>()
      return try {
          @Suppress("UNCHECKED_CAST")
          conversionService.fromValues(values, typeInfo) as R
      } catch (cause: Exception) {
          throw ParameterConversionException(name, typeInfo.type.simpleName ?:
          typeInfo.type.toString(), cause)
      }
    }

    /**
     * Gets parameter value associated with this name or throws if the name is not present.
     * Converting to type R using ConversionService.
     *
     * Throws:
     *   MissingRequestParameterException - when parameter is missing
     *   ParameterConversionException - when conversion from String to R fails
     */
    private inline fun <reified R : Any> Parameters.getTypedOrFail(name: String,
        conversionService: ConversionService = DefaultConversionService): R {
      val values = getAll(name) ?: throw MissingRequestParameterException(name)
      val typeInfo = typeInfo<R>()
      return try {
          @Suppress("UNCHECKED_CAST")
          conversionService.fromValues(values, typeInfo) as R
      } catch (cause: Exception) {
          throw ParameterConversionException(name, typeInfo.type.simpleName ?:
          typeInfo.type.toString(), cause)
      }
    }

    /**
     * Gets first value from the list of values associated with a name.
     *
     * Throws:
     *   BadRequestException - when the name is not present
     */
    private fun Headers.getOrFail(name: String): String = this[name] ?: throw
        BadRequestException("Header " + name + " is required")
  }
}
