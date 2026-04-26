package com.example.authn

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

@Serializable
data class UserSession(
    val userId: String,
    val accessToken: String,
    val refreshToken: String?,
    val expiration: Instant,
) {
    // Sized for Discord's 7-day token lifetime; adjust to match your provider's expiry
    fun isExpiredOrExpiringSoon(buffer: Duration = 1.days): Boolean =
        Clock.System.now() >= expiration - buffer
}
