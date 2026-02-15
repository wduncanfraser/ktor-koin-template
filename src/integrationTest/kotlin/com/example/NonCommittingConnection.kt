package com.example

import java.sql.Connection

/**
 * A [Connection] wrapper that suppresses [commit] calls.
 * Used in integration tests to keep all operations within a single transaction
 * that is rolled back after each test via the underlying [delegate] connection.
 */
class NonCommittingConnection(private val delegate: Connection) : Connection by delegate {
    override fun commit() {
        // Intentionally suppressed — rollback is called on the delegate in afterEach
    }

    override fun setAutoCommit(autoCommit: Boolean) {
        // Prevent jOOQ or other frameworks from enabling auto-commit
    }
}
