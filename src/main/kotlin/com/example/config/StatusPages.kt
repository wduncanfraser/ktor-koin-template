package com.example.config

import com.example.core.api.exceptions.ProblemDetailsException
import com.example.generated.api.models.ProblemDetailsContract
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.request.uri
import io.ktor.server.response.respondText
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

/**
 * Application Call extension to return [ProblemDetailsContract] with correct content type in ktor
 */
suspend fun ApplicationCall.respondProblem(
    status: HttpStatusCode,
    problem: ProblemDetailsContract,
) {
    respondText(
        Json.encodeToString(problem),
        ContentType.Application.ProblemJson,
        status,
    )
}

/**
 * Configure status pages for RFC 9457 [ProblemDetailsContract]
 */
fun StatusPagesConfig.configureStatusPages() {
    exception<Throwable> { call, cause ->
        val statusCode = HttpStatusCode.InternalServerError

        when (cause) {
            is BadRequestException -> {
                logger.debug(cause) { "Bad request at uri ${call.request.uri}" }

                val cause = cause.cause ?: cause
                val statusCode = HttpStatusCode.BadRequest

                call.respondProblem(
                    statusCode,
                    ProblemDetailsContract(
                        type = "https://example.com/errors/bad-request",
                        title = statusCode.description,
                        status = statusCode.value,
                        detail = cause.localizedMessage,
                        instance = call.request.uri
                    )
                )
            }

            is ProblemDetailsException -> {
                logger.debug(cause.cause) { "Problem details in exception" }
                call.respondProblem(
                    cause.statusCode,
                    ProblemDetailsContract(
                        type = cause.type,
                        title = cause.statusCode.description,
                        status = cause.statusCode.value,
                        detail = cause.message,
                        instance = call.request.uri
                    ),
                )
            }

            else -> {
                logger.error(cause) { "Unhandled exception in controller" }
                call.respondProblem(
                    statusCode,
                    ProblemDetailsContract(
                        type = "https://example.com/errors/server-error",
                        title = statusCode.description,
                        status = statusCode.value,
                        detail = "Unhandled error, please try again later",
                        instance = call.request.uri
                    )
                )
            }
        }
    }

}
