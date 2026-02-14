package com.example.authn

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.server.auth.OAuthAccessTokenResponse
import kotlinx.serialization.Serializable
import kotlin.time.Instant

private val logger = KotlinLogging.logger {}

class DiscordOAuthProvider(
    private val httpClient: HttpClient,
) {
    suspend fun buildUserSession(principal: OAuthAccessTokenResponse.OAuth2): UserSession {
        val accessToken = principal.accessToken
        val profile = httpClient.get("https://discord.com/api/oauth2/@me") {
            headers[HttpHeaders.Authorization] = "Bearer $accessToken"
            expectSuccess = true
        }
        val bodyText = profile.bodyAsText()
        logger.info { "Received profile: $bodyText" }
        val body = profile.body<DiscordAuthorizationResponse>()
        return UserSession(
            userId = body.user.id,
            accessToken = accessToken,
            refreshToken = principal.refreshToken,
            expiration = body.expires,
        )
    }
}

@Serializable
data class DiscordUser(
    val id: String,
    val username: String,
    val discriminator: String,
    val avatar: String?,
)

@Serializable
data class DiscordAuthorizationResponse(
    val scopes: List<String>,
    val expires: Instant,
    val user: DiscordUser,
)
