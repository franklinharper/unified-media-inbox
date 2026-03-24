package com.franklinharper.social.media.client.auth

data class AuthenticatedUser(val userId: String, val email: String)

data class ServerSession(val token: String, val userId: String, val expiresAtEpochMillis: Long)
