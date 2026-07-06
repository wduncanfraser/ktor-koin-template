package com.example.config

import com.example.authn.OAuthProcessingException
import com.example.core.api.ProblemDetailsDefaults
import com.example.core.api.exceptions.ProblemDetailsException
import com.example.generated.api.models.ProblemDetailsContract
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.request.uri
import io.ktor.server.response.respondText
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

private fun ApplicationCall.problemInstance(): String =
    callId?.let { "urn:request:$it" } ?: request.uri

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
                        type = ProblemDetailsDefaults.BadRequest.TYPE,
                        title = statusCode.description,
                        status = statusCode.value,
                        detail = cause.localizedMessage,
                        instance = call.problemInstance()
                    )
                )
            }

            is OAuthProcessingException -> {
                logger.warn(cause) { "OAuth processing failed at uri ${call.request.uri}" }
                val statusCode = HttpStatusCode.Unauthorized
                call.respondProblem(
                    statusCode,
                    ProblemDetailsContract(
                        type = ProblemDetailsDefaults.Unauthorized.TYPE,
                        title = statusCode.description,
                        status = statusCode.value,
                        detail = ProblemDetailsDefaults.Unauthorized.MESSAGE,
                        instance = call.problemInstance()
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
                        instance = call.problemInstance(),
                        errors = cause.errors,
                    ),
                )
            }

            else -> {
                logger.error(cause) { "Unhandled exception in controller" }
                call.respondProblem(
                    statusCode,
                    ProblemDetailsContract(
                        type = ProblemDetailsDefaults.ServerError.TYPE,
                        title = statusCode.description,
                        status = statusCode.value,
                        detail = ProblemDetailsDefaults.ServerError.MESSAGE,
                        instance = call.problemInstance()
                    )
                )
            }
        }
    }

}
