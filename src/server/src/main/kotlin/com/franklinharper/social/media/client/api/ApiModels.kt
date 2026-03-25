package com.franklinharper.social.media.client.api

import kotlinx.serialization.Serializable

@Serializable
data class SignInRequest(
    val email: String,
    val password: String,
)

@Serializable
data class AuthUserResponse(
    val userId: String,
    val email: String,
)

@Serializable
data class AuthSessionResponse(
    val token: String,
    val user: AuthUserResponse,
)
