package com.example.generated.api.controllers

import com.example.generated.api.models.CreateTodoRequestContract
import com.example.generated.api.models.ListTodosResponseContract
import com.example.generated.api.models.TodoResponseContract
import com.example.generated.api.models.UpdateTodoRequestContract
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
import kotlin.Boolean
import kotlin.Int
import kotlin.String

public interface TodosController {
  /**
   * List all todos
   *
   * Route is expected to respond with [com.example.generated.api.models.ListTodosResponseContract].
   * Use [com.example.generated.api.controllers.TypedApplicationCall.respondTyped] to send the
   * response.
   *
   * @param pageSize The number of items to return in a single request.
   * @param page The page number of results to return.
   * @param completed Filter for todos to only list completed vs incomplete records.
   * @param call Decorated ApplicationCall with additional typed respond methods
   */
  public suspend fun listTodos(
    pageSize: Int?,
    page: Int?,
    completed: Boolean?,
    call: TypedApplicationCall<ListTodosResponseContract>,
  )

  /**
   * Create a todo
   *
   * Route is expected to respond with [com.example.generated.api.models.TodoResponseContract].
   * Use [com.example.generated.api.controllers.TypedApplicationCall.respondTyped] to send the
   * response.
   *
   * @param createTodoRequest The request body for creating a Todo.
   * @param call Decorated ApplicationCall with additional typed respond methods
   */
  public suspend fun createTodo(createTodoRequest: CreateTodoRequestContract,
      call: TypedApplicationCall<TodoResponseContract>)

  /**
   * Get a todo
   *
   * Route is expected to respond with [com.example.generated.api.models.TodoResponseContract].
   * Use [com.example.generated.api.controllers.TypedApplicationCall.respondTyped] to send the
   * response.
   *
   * @param todoId The unique identifier of the Todo.
   * @param call Decorated ApplicationCall with additional typed respond methods
   */
  public suspend fun getTodo(todoId: String, call: TypedApplicationCall<TodoResponseContract>)

  /**
   * Update a todo
   *
   * Route is expected to respond with [com.example.generated.api.models.TodoResponseContract].
   * Use [com.example.generated.api.controllers.TypedApplicationCall.respondTyped] to send the
   * response.
   *
   * @param updateTodoRequest The request body for updating a Todo.
   * @param todoId The unique identifier of the Todo.
   * @param call Decorated ApplicationCall with additional typed respond methods
   */
  public suspend fun updateTodo(
    todoId: String,
    updateTodoRequest: UpdateTodoRequestContract,
    call: TypedApplicationCall<TodoResponseContract>,
  )

  /**
   * Delete a todo
   *
   * Route is expected to respond with status 204.
   * Use [io.ktor.server.response.respond] to send the response.
   *
   * @param todoId The unique identifier of the Todo.
   * @param call The Ktor application call
   */
  public suspend fun deleteTodo(todoId: String, call: ApplicationCall)

  public companion object {
    /**
     * Mounts all routes for the Todos resource
     *
     * - GET /todos List all todos
     * - POST /todos Create a todo
     * - GET /todos/{todo-id} Get a todo
     * - PUT /todos/{todo-id} Update a todo
     * - DELETE /todos/{todo-id} Delete a todo
     */
    public fun Route.todosRoutes(controller: TodosController) {
      `get`("/todos") {
        val pageSize = call.request.queryParameters.getTyped<kotlin.Int>("pageSize")
        val page = call.request.queryParameters.getTyped<kotlin.Int>("page")
        val completed = call.request.queryParameters.getTyped<kotlin.Boolean>("completed")
        controller.listTodos(pageSize, page, completed, TypedApplicationCall(call))
      }
      post("/todos") {
        val createTodoRequest = call.receive<CreateTodoRequestContract>()
        controller.createTodo(createTodoRequest, TypedApplicationCall(call))
      }
      `get`("/todos/{todo-id}") {
        val todoId = call.parameters.getTypedOrFail<kotlin.String>("todo-id")
        controller.getTodo(todoId, TypedApplicationCall(call))
      }
      put("/todos/{todo-id}") {
        val todoId = call.parameters.getTypedOrFail<kotlin.String>("todo-id")
        val updateTodoRequest = call.receive<UpdateTodoRequestContract>()
        controller.updateTodo(todoId, updateTodoRequest, TypedApplicationCall(call))
      }
      delete("/todos/{todo-id}") {
        val todoId = call.parameters.getTypedOrFail<kotlin.String>("todo-id")
        controller.deleteTodo(todoId, call)
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
