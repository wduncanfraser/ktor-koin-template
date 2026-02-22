package com.example.authn

import io.ktor.server.sessions.*
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.time.toJavaInstant

/**
 * [SessionStorage] leveraging redis.
 * Sessions are stored with a key of "session:<session-id>". Additionally, all sessions for a given user are stored
 * under the sorted set with a key of `user:<user-id>:sessions
 */
class RedisSessionStorage(
    private val redisConnection: StatefulRedisConnection<String, String>,
    private val redisDispatcher: CoroutineDispatcher,
) : SessionStorage {
    override suspend fun write(id: String, value: String) {
        val session = Json.decodeFromString<UserSession>(value)
        val expiration = session.expiration.toJavaInstant()
        val expirationMillis = expiration.toEpochMilli()
        withContext(redisDispatcher) {
            val api = redisConnection.sync()
            // Set session with the expiration aligning with token expiration
            api.set(buildSessionKey(id), value, SetArgs.Builder.exAt(expiration))
            // Index the session ID against the user
            api.zadd("$USER_KEY:${session.userId}:$SESSIONS_KEY", expirationMillis.toDouble(), id)
        }
    }

    override suspend fun invalidate(id: String) {
        withContext(redisDispatcher) {
            val api = redisConnection.sync()
            // If the session exists, remove the user index value
            api.get(buildSessionKey(id))?.let {
                val session = Json.decodeFromString<UserSession>(it)
                api.zrem(buildUserSessionsKey(session.userId), buildSessionKey(id))
            }
            api.del(buildSessionKey(id))
        }
    }

    override suspend fun read(id: String): String {
        return withContext(redisDispatcher) {
            val api = redisConnection.sync()
            api.get(buildSessionKey(id)) ?: throw NoSuchElementException("Session $id not found")
        }
    }

    suspend fun listSessionsByUser(userId: String): List<UserSession> {
        return withContext(redisDispatcher) {
            val api = redisConnection.sync()
            val sessionKeys = api.zrange(buildUserSessionsKey(userId), 0, -1).map {
                buildSessionKey(it)
            }.toList()
            @Suppress("SpreadOperator")
            api.mget(*sessionKeys.toTypedArray()).mapNotNull {
                it.getValueOrElse(null)?.let { session ->
                    Json.decodeFromString<UserSession>(session)
                }
            }.toList()
        }
    }

    companion object {
        const val SESSION_KEY = "session"
        const val SESSIONS_KEY = "sessions"
        const val USER_KEY = "user"

        fun buildSessionKey(sessionId: String): String = "$SESSION_KEY:$sessionId"
        fun buildUserSessionsKey(userId: String): String = "$USER_KEY:$userId:$SESSIONS_KEY"
    }
}
