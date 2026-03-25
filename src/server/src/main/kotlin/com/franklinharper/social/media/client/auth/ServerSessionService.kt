package com.franklinharper.social.media.client.auth

import com.franklinharper.social.media.client.db.SocialMediaDatabase
import java.util.UUID

class ServerSessionService(
    private val database: SocialMediaDatabase,
    private val passwordHasher: PasswordHasher = PasswordHasher(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val userIdGenerator: () -> String = { UUID.randomUUID().toString() },
    private val sessionTokenGenerator: () -> String = { UUID.randomUUID().toString() },
    private val sessionTtlMillis: Long = DEFAULT_SESSION_TTL_MILLIS,
) {
    private val queries = database.socialMediaDatabaseQueries

    suspend fun createUser(email: String, password: String): AuthenticatedUser {
        val normalizedEmail = normalizeEmail(email)
        val user = AuthenticatedUser(
            userId = userIdGenerator(),
            email = normalizedEmail,
        )

        database.transaction {
            if (queries.selectUserByEmail(normalizedEmail).executeAsOneOrNull() != null) {
                throw UserAlreadyExistsException(normalizedEmail)
            }
            queries.insertUser(
                user_id = user.userId,
                email = user.email,
            )
            queries.insertUserPasswordCredential(
                user_id = user.userId,
                password_hash = passwordHasher.hash(password),
            )
        }

        return user
    }

    suspend fun signIn(email: String, password: String): ServerSession {
        val credential = queries.selectUserPasswordCredentialByEmail(normalizeEmail(email))
            .executeAsOneOrNull() ?: throw InvalidCredentialsException()

        if (!passwordHasher.verify(password, credential.password_hash)) {
            throw InvalidCredentialsException()
        }

        return createSession(credential.user_id)
    }

    suspend fun createSession(userId: String): ServerSession {
        queries.selectUserById(userId).executeAsOneOrNull() ?: throw UnknownUserException(userId)

        val session = ServerSession(
            token = sessionTokenGenerator(),
            userId = userId,
            expiresAtEpochMillis = clock() + sessionTtlMillis,
        )
        queries.insertServerSession(
            token = session.token,
            user_id = session.userId,
            expires_at_epoch_millis = session.expiresAtEpochMillis,
        )
        return session
    }

    suspend fun requireUser(token: String): AuthenticatedUser {
        val session = queries.selectServerSession(token).executeAsOneOrNull() ?: throw UnknownSessionException(token)

        if (session.expires_at_epoch_millis <= clock()) {
            queries.removeServerSession(token)
            throw ExpiredSessionException(token)
        }

        val user = queries.selectUserById(session.user_id).executeAsOneOrNull() ?: throw MissingSessionUserException(token)
        return AuthenticatedUser(user.user_id, user.email)
    }

    suspend fun revokeSession(token: String) {
        queries.removeServerSession(token)
    }

    private fun normalizeEmail(email: String): String = email.trim().lowercase()

    private companion object {
        private const val DEFAULT_SESSION_TTL_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
