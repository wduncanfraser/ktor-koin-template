package com.example.core.api

/**
 * Well-known `type`/message pairs for RFC 9457 problem responses, shared across the places that
 * build them (exception throw sites, StatusPages handlers) so a given problem category always
 * reads the same regardless of which code path produced it.
 *
 * Some categories only define `TYPE`: their message legitimately varies per call site (e.g.
 * [NotFound] embeds the missing id, [BadRequest] echoes the parse failure) and hardcoding one here
 * would be wrong, not just redundant. Only add a `MESSAGE` constant when every call site already
 * uses the exact same text.
 */
object ProblemDetailsDefaults {
    object Unauthorized {
        const val TYPE = "https://example.com/errors/unauthorized"
        const val MESSAGE = "Authentication failed, please try again"
    }

    object NotFound {
        const val TYPE = "https://example.com/errors/not-found"
    }

    object Forbidden {
        const val TYPE = "https://example.com/errors/forbidden"
    }

    object ValidationFailed {
        const val TYPE = "https://example.com/errors/unprocessable-entity"
        const val MESSAGE = "Request validation failed"
    }

    object BadRequest {
        const val TYPE = "https://example.com/errors/bad-request"
    }

    object ServerError {
        const val TYPE = "https://example.com/errors/server-error"
        const val MESSAGE = "Unhandled error, please try again later"
    }
}
