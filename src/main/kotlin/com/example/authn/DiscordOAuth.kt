package com.example.authn

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.forms.submitForm
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.server.auth.OAuthAccessTokenResponse
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
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
        val body = profile.body<DiscordAuthorizationResponse>()
        logger.debug { "OAuth profile received for userId=${body.user.id} username=${body.user.username}" }
        return UserSession(
            userId = body.user.id,
            accessToken = accessToken,
            refreshToken = principal.refreshToken,
            expiration = body.expires,
        )
    }

    suspend fun refreshSession(session: UserSession, clientId: String, clientSecret: String): UserSession {
        @Suppress("TooGenericExceptionCaught")
        return try {
            val tokenResponse = httpClient.submitForm(
                url = "https://discord.com/api/oauth2/token",
                formParameters = Parameters.build {
                    append("grant_type", "refresh_token")
                    append("refresh_token", session.refreshToken!!)
                    append("client_id", clientId)
                    append("client_secret", clientSecret)
                },
            ) { expectSuccess = true }.body<DiscordTokenResponse>()
            session.copy(
                accessToken = tokenResponse.accessToken,
                refreshToken = tokenResponse.refreshToken,
                expiration = Clock.System.now() + tokenResponse.expiresIn.seconds,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw OAuthProcessingException("Token refresh failed for userId=${session.userId}", e)
        }
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

@Serializable
data class DiscordTokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("expires_in")
    val expiresIn: Long,
)
