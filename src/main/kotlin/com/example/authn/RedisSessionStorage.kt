package com.example.authn

import io.ktor.server.sessions.*
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import kotlin.time.toJavaInstant

/**
 * [SessionStorage] leveraging redis.
 * Sessions are stored with a key of "session:<session-id>". Additionally, all sessions for a given user are stored
 * under the sorted set with a key of `user:<user-id>:sessions
 */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
class RedisSessionStorage(
    private val redisConnection: StatefulRedisConnection<String, String>,
) : SessionStorage {
    override suspend fun write(id: String, value: String) {
        val session = Json.decodeFromString<UserSession>(value)
        val expiration = session.expiration.toJavaInstant()
        val expirationMillis = expiration.toEpochMilli()
        val api = redisConnection.sync()
        // Set session with the expiration aligning with token expiration
        api.set(buildSessionKey(id), value, SetArgs.Builder.exAt(expiration))
        // Index the session ID against the user
        api.zadd("$USER_KEY:${session.userId}:$SESSIONS_KEY", expirationMillis.toDouble(), id)
    }

    override suspend fun invalidate(id: String) {
        val api = redisConnection.sync()
        // If the session exists, remove the user index value
        api.get(buildSessionKey(id))?.let {
            val session = Json.decodeFromString<UserSession>(it)
            api.zrem(buildUserSessionsKey(session.userId), buildSessionKey(id))
        }
        api.del(buildSessionKey(id))
    }

    // TODO: using coroutines API under stress testing leads to thread deadlock and starvation
    override suspend fun read(id: String): String {
        val api = redisConnection.sync()
        return api.get(buildSessionKey(id)) ?: throw NoSuchElementException("Session $id not found")
    }

    suspend fun listSessionsByUser(userId: String): List<UserSession> {
        val api = redisConnection.coroutines()
        val sessionKeys = api.zrange(buildUserSessionsKey(userId), 0, -1).map {
            buildSessionKey(it)
        }.toList()
        @Suppress("SpreadOperator")
        return api.mget(*sessionKeys.toTypedArray()).mapNotNull {
            it.getValueOrElse(null)?.let { session ->
                Json.decodeFromString<UserSession>(session)
            }
        }.toList()
    }

    companion object {
        const val SESSION_KEY = "session"
        const val SESSIONS_KEY = "sessions"
        const val USER_KEY = "user"

        fun buildSessionKey(sessionId: String): String = "$SESSION_KEY:$sessionId"
        fun buildUserSessionsKey(userId: String): String = "$USER_KEY:$userId:$SESSIONS_KEY"
    }
}
