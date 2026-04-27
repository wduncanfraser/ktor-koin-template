package com.example.generated.api.controllers

import com.example.generated.api.models.ListTodosResponseContract
import io.ktor.http.Headers
import io.ktor.http.Parameters
import io.ktor.server.application.call
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.MissingRequestParameterException
import io.ktor.server.plugins.ParameterConversionException
import io.ktor.server.routing.Route
import io.ktor.server.routing.`get`
import io.ktor.util.converters.ConversionService
import io.ktor.util.converters.DefaultConversionService
import io.ktor.util.reflect.typeInfo
import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String

public interface TodosController {
  /**
   * List all todos for the authenticated user
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

  public companion object {
    /**
     * Mounts all routes for the Todos resource
     *
     * - GET /todos List all todos for the authenticated user
     */
    public fun Route.todosRoutes(controller: TodosController) {
      `get`("/todos") {
        val pageSize = call.request.queryParameters.getTyped<kotlin.Int>("pageSize")
        val page = call.request.queryParameters.getTyped<kotlin.Int>("page")
        val completed = call.request.queryParameters.getTyped<kotlin.Boolean>("completed")
        controller.listTodos(pageSize, page, completed, TypedApplicationCall(call))
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
