package com.franklinharper.social.media.client.auth

data class AuthenticatedUser(val userId: String, val email: String)

data class ServerSession(val token: String, val userId: String, val expiresAtEpochMillis: Long)

sealed class AuthFailure(message: String) : IllegalStateException(message)

class InvalidCredentialsException : AuthFailure("Invalid credentials")

class UserAlreadyExistsException(email: String) : AuthFailure("User already exists: $email")

class UnknownUserException(userId: String) : AuthFailure("Unknown user: $userId")

class UnknownSessionException(token: String) : AuthFailure("Unknown session: $token")

class ExpiredSessionException(token: String) : AuthFailure("Expired session: $token")

class MissingSessionUserException(token: String) : AuthFailure("Missing user for session: $token")
