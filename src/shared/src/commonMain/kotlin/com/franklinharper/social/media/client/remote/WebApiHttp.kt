package com.franklinharper.social.media.client.remote

import com.franklinharper.social.media.client.client.NetworkHttp
import com.franklinharper.social.media.client.domain.ClientError
import com.franklinharper.social.media.client.domain.ClientFailure
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class WebApiResponse(
    val statusCode: Int,
    val body: String,
)

interface WebApiHttp {
    var bearerToken: String?

    suspend fun get(path: String): WebApiResponse
    suspend fun post(path: String, body: String? = null): WebApiResponse
    suspend fun delete(path: String, body: String? = null): WebApiResponse
}

class DefaultWebApiHttp(
    private val baseUrl: String,
) : WebApiHttp {
    override var bearerToken: String? = null

    override suspend fun get(path: String): WebApiResponse =
        NetworkHttp.get(
            url = url(path),
            headers = authHeaders(),
        ).toWebApiResponse()

    override suspend fun post(path: String, body: String?): WebApiResponse =
        NetworkHttp.postJson(
            url = url(path),
            body = body.orEmpty(),
            headers = authHeaders(),
        ).toWebApiResponse()

    override suspend fun delete(path: String, body: String?): WebApiResponse =
        NetworkHttp.deleteJson(
            url = url(path),
            body = body,
            headers = authHeaders(),
        ).toWebApiResponse()

    private fun url(path: String): String =
        if (path.startsWith("http://") || path.startsWith("https://")) {
            path
        } else {
            "${baseUrl.trimEnd('/')}/${path.trimStart('/')}"
        }

    private fun authHeaders(): Map<String, String> =
        bearerToken?.let { token -> mapOf(HttpHeaders.Authorization to "Bearer $token") }.orEmpty()
}

class WebApiException(
    override val clientError: ClientError,
) : RuntimeException(clientError.toString()), ClientFailure

internal val webApiJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal fun WebApiResponse.requireSuccess(): WebApiResponse {
    if (statusCode in 200..299) return this
    throw when (statusCode) {
        400 -> WebApiException(ClientError.PermanentFailure(body.ifBlank { null }))
        401 -> WebApiException(ClientError.AuthenticationError())
        403 -> WebApiException(ClientError.AuthenticationError())
        in 500..599 -> WebApiException(ClientError.TemporaryFailure("Server error: $statusCode"))
        else -> WebApiException(ClientError.TemporaryFailure("HTTP $statusCode"))
    }
}

internal fun <T> WebApiResponse.decodeSuccess(deserializer: kotlinx.serialization.DeserializationStrategy<T>): T =
    webApiJson.decodeFromString(deserializer, requireSuccess().body)

internal fun WebApiResponse.requireNoContentSuccess() {
    requireSuccess()
}

@Serializable
internal data class SignInRequestDto(
    val email: String,
    val password: String,
)

@Serializable
internal data class AuthUserResponseDto(
    val userId: String,
    val email: String,
)

@Serializable
internal data class AuthSessionResponseDto(
    val token: String,
    val user: AuthUserResponseDto,
)

@Serializable
internal data class SourceDto(
    val platformId: String,
    val kind: String,
    val value: String,
)

@Serializable
internal data class AddSourceRequestDto(
    val platformId: String,
    val kind: String,
    val value: String,
)

@Serializable
internal data class ListSourcesResponseDto(
    val sources: List<SourceDto>,
)

@Serializable
internal data class FeedSourceDto(
    val platformId: String,
    val sourceId: String,
    val displayName: String,
)

@Serializable
internal data class FeedItemDto(
    val itemId: String,
    val platformId: String,
    val source: FeedSourceDto,
    val authorName: String?,
    val title: String?,
    val body: String?,
    val permalink: String?,
    val commentsPermalink: String?,
    val publishedAtEpochMillis: Long,
    val seen: Boolean,
)

@Serializable
internal data class FeedSourceStatusDto(
    val source: FeedSourceDto,
    val state: String,
    val contentOrigin: String,
    val errorKind: String? = null,
    val errorMessage: String? = null,
)

@Serializable
internal data class FeedResponseDto(
    val items: List<FeedItemDto>,
    val sourceStatuses: List<FeedSourceStatusDto> = emptyList(),
)

@Serializable
internal data class MarkSeenRequestDto(
    val itemIds: List<String>,
)

private fun com.franklinharper.social.media.client.client.NetworkResponse.toWebApiResponse(): WebApiResponse =
    WebApiResponse(
        statusCode = statusCode,
        body = body,
    )
