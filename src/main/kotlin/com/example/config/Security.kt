package com.example.config

import com.example.authn.DiscordOAuthProvider
import com.example.authn.OAuthProcessingException
import com.example.authn.RedisSessionStorage
import com.example.authn.UserSession
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.config.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

fun Application.configureSecurity() {
    val authConfig: AuthenticationConfig = property("authentication")
    val httpClient by inject<HttpClient>()
    val redisConnection by inject<StatefulRedisConnection<String, String>>()
    val discordOAuthProvider by inject<DiscordOAuthProvider>()
    val sessionStorage = RedisSessionStorage(redisConnection)
    // TODO: For now, wrap the session provider in an in-memory cache so we don't make a blocking
    //  network call on every request.
    //  Should be able to remove this once coroutine API issues with lettuce are resolved
    val cachedSessionStorage = CacheStorage(
        delegate = sessionStorage,
        idleTimeout = 60000L
    )

    install(Sessions) {
        val secretSignKey = hex(authConfig.sessionSigningKey)
        cookie<UserSession>(
            name = authConfig.sessionCookieName,
            storage = cachedSessionStorage,
        ) {
            cookie.path = "/"
            transform(SessionTransportTransformerMessageAuthentication(secretSignKey))
        }
    }

    install(Authentication) {
        session<UserSession>("auth-session") {
            validate {
                it
            }
            challenge {
                call.respondRedirect("/login")
            }
        }

        oauth("auth-oauth-discord") {
            urlProvider = { authConfig.oAuth.callbackUrl }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "discord",
                    authorizeUrl = "https://discord.com/oauth2/authorize",
                    accessTokenUrl = "https://discord.com/api/oauth2/token",
                    requestMethod = HttpMethod.Post,
                    clientId = authConfig.oAuth.clientId,
                    clientSecret = authConfig.oAuth.clientSecret,
                    defaultScopes = listOf("identify")
                )
            }
            client = httpClient
        }
    }

    routing {
        authenticate("auth-oauth-discord") {
            get("/login") {}

            get("/callback") {
                val principal: OAuthAccessTokenResponse.OAuth2 = call.authentication.principal()
                    ?: throw OAuthProcessingException("Access token is null in OAuth2 payload")
                val session = discordOAuthProvider.buildUserSession(principal)
                call.sessions.set(session)
                call.respondRedirect("/api/v1/todos")
            }
        }

        authenticate("auth-session") {
            get("/sessions") {
                call.sessions.get<UserSession>()?.let { session ->
                    val sessions = sessionStorage.listSessionsByUser(session.userId)
                    call.respond(sessions)
                }
            }
        }

        get("/logout") {
            call.sessions.clear<UserSession>()
            call.respondRedirect("/login")
        }
    }
}

@Serializable
data class AuthenticationConfig(
    val sessionCookieName: String,
    val sessionSigningKey: String,
    val oAuth: OAuthConfig,
)

@Serializable
data class OAuthConfig(
    val callbackUrl: String,
    val clientId: String,
    val clientSecret: String,
)
