package com.example.core.repository

import java.time.Duration

object RepositoryConsts {
    private const val DEFAULT_LOCK_TIMEOUT_SECONDS = 5L
    val DEFAULT_LOCK_TIMEOUT: Duration = Duration.ofSeconds(DEFAULT_LOCK_TIMEOUT_SECONDS)
}
