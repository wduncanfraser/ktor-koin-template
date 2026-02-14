package com.example.authn

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class UserSession(
    val userId: String,
    val accessToken: String,
    val refreshToken: String?,
    val expiration: Instant,
)
