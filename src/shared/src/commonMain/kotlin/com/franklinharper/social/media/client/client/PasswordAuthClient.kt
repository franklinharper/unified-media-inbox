package com.franklinharper.social.media.client.client

import com.franklinharper.social.media.client.domain.AccountSession

interface PasswordAuthClient {
    suspend fun signIn(identifier: String, password: String): AccountSession
}
