package com.example.generated.api.controllers

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlin.Any
import kotlin.Suppress

/**
 * Decorator for Ktor's ApplicationCall that provides type safe variants of the [respond] functions.
 *
 * It can be used as a drop-in replacement for [io.ktor.server.application.ApplicationCall].
 *
 * @param R The type of the response body
 */
public class TypedApplicationCall<R : Any>(
  private val applicationCall: ApplicationCall,
) : ApplicationCall by applicationCall {
  @Suppress("unused")
  public suspend inline fun <reified T : R> respondTyped(message: T) {
    respond(message)
  }

  @Suppress("unused")
  public suspend inline fun <reified T : R> respondTyped(status: HttpStatusCode, message: T) {
    respond(status, message)
  }
}
