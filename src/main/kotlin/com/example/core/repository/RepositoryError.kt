package com.example.core.repository

/**
 * All error conditions that can happen in a repository query
 */
sealed class RepositoryError {
    /**
     * Error type used when a specified record is not found in a direct query
     */
    data object RecordNotFound : RepositoryError()

    /**
     * Error for when a record violates a database constraint
     */
    data class RecordConstraintViolation(val t: Throwable) : RepositoryError()

    /**
     * Error type used when an unexpected exception is thrown
     */
    data class UnhandledException(val t: Throwable) : RepositoryError()
}
