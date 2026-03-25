package com.franklinharper.social.media.client.client

data class NetworkResponse(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String>,
)

internal expect object NetworkHttp {
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): NetworkResponse

    suspend fun postJson(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
    ): NetworkResponse

    suspend fun deleteJson(
        url: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): NetworkResponse
}

internal expect fun networkHttpEngineName(): String
