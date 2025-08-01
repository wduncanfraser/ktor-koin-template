package com.example.authn

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class UserSession(
    val userId: String,
    val accessToken: String,
    val refreshToken: String?,
    val expiration: Instant,
)
