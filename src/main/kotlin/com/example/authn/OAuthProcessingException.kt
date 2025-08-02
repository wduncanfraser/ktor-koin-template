package com.example.authn

data class OAuthProcessingException(
    override val message: String,
    override val cause: Throwable? = null,
): RuntimeException()
