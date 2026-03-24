package com.franklinharper.social.media.client.client.twitter

import com.franklinharper.social.media.client.client.NetworkHttp
import com.franklinharper.social.media.client.client.NetworkResponse
import com.franklinharper.social.media.client.client.SocialPlatformClient
import com.franklinharper.social.media.client.domain.AccountSession
import com.franklinharper.social.media.client.domain.ClientError
import com.franklinharper.social.media.client.domain.ClientFailure
import com.franklinharper.social.media.client.domain.FeedCursor
import com.franklinharper.social.media.client.domain.FeedItem
import com.franklinharper.social.media.client.domain.FeedPage
import com.franklinharper.social.media.client.domain.FeedQuery
import com.franklinharper.social.media.client.domain.FeedSource
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SeenState
import com.franklinharper.social.media.client.domain.SessionState
import com.franklinharper.social.media.client.domain.SocialProfile
import io.ktor.http.encodeURLParameter
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val twitterJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

class TwitterClient(
    private val sessionProvider: suspend () -> AccountSession? = { null },
    private val fetchRecentTweets: suspend (List<String>, String?, String) -> NetworkResponse = { handles, cursor, bearerToken ->
        NetworkHttp.get(
            url = buildRecentTweetsUrl(handles, cursor),
            headers = mapOf("Authorization" to "Bearer $bearerToken"),
        )
    },
    private val fetchProfileByUsername: suspend (String, String) -> NetworkResponse = { handle, bearerToken ->
        NetworkHttp.get(
            url = buildProfileUrl(handle),
            headers = mapOf("Authorization" to "Bearer $bearerToken"),
        )
    },
) : SocialPlatformClient {
    override val id: PlatformId = PlatformId.Twitter
    override val displayName: String = "Twitter"

    override suspend fun sessionState(): SessionState =
        sessionProvider()?.takeIf { !it.accessToken.isNullOrBlank() }?.let(SessionState::SignedIn) ?: SessionState.SignedOut

    override suspend fun loadProfile(accountId: String): SocialProfile {
        val handle = normalizeHandle(accountId)
        val bearerToken = requireAccessToken()
        val response = try {
            fetchProfileByUsername(handle, bearerToken).bodyOrThrow()
        } catch (error: Throwable) {
            throw error.asTwitterException(defaultMessage = "Unable to load Twitter profile")
        }
        return try {
            val profile = twitterJson.decodeFromString<GetUserByUsernameResponse>(response).data
                ?: throw TwitterClientException(ClientError.ParsingError("Missing profile data"))
            SocialProfile(
                accountId = profile.id,
                displayName = profile.name,
                handle = profile.username,
            )
        } catch (error: Throwable) {
            throw error.asTwitterParsingException()
        }
    }

    override suspend fun loadFeed(query: FeedQuery, cursor: FeedCursor?): FeedPage {
        val socialQuery = query as? FeedQuery.SocialUsers
            ?: throw TwitterClientException(ClientError.PermanentFailure("TwitterClient requires SocialUsers query"))
        val bearerToken = requireAccessToken()
        val requestedUsersByNormalizedHandle = socialQuery.users.associateBy(::normalizedHandleKey)
        val response = try {
            fetchRecentTweets(socialQuery.users, cursor?.value ?: socialQuery.cursor?.value, bearerToken).bodyOrThrow()
        } catch (error: Throwable) {
            throw error.asTwitterException(defaultMessage = "Unable to load Twitter feed")
        }
        val page = try {
            twitterJson.decodeFromString<SearchRecentTweetsResponse>(response)
        } catch (error: Throwable) {
            throw error.asTwitterParsingException()
        }

        val usersById = page.includes?.users.orEmpty().associateBy(TwitterUser::id)
        val items = page.data.orEmpty().mapNotNull { tweet ->
            val author = usersById[tweet.authorId]
            val sourceUser = author?.username
                ?.let(::normalizedHandleKey)
                ?.let(requestedUsersByNormalizedHandle::get)
                ?: return@mapNotNull null
            tweet.toFeedItem(
                sourceUser = sourceUser,
                author = author,
            )
        }.sortedByDescending(FeedItem::publishedAtEpochMillis)

        return FeedPage(items = items, nextCursor = page.meta?.nextToken?.let(::FeedCursor))
    }

    private suspend fun requireAccessToken(): String =
        sessionProvider()?.accessToken?.takeIf(String::isNotBlank)
            ?: throw TwitterClientException(ClientError.AuthenticationError("Missing bearer token"))
    private companion object {
        fun buildRecentTweetsUrl(handles: List<String>, cursor: String?): String = buildString {
            val normalizedHandles = handles.map(::normalizeHandle).distinct()
            val query = buildString {
                if (normalizedHandles.size > 1) append('(')
                append(normalizedHandles.joinToString(" OR ") { handle -> "from:$handle" })
                if (normalizedHandles.size > 1) append(')')
                append(" -is:retweet")
            }
            val maxResults = (normalizedHandles.size * 10).coerceIn(10, 100)
            append("https://api.x.com/2/tweets/search/recent?query=")
            append(query.urlEncode())
            append("&max_results=")
            append(maxResults)
            append("&tweet.fields=author_id,created_at,text")
            append("&expansions=author_id")
            append("&user.fields=name,username")
            if (!cursor.isNullOrBlank()) {
                append("&next_token=")
                append(cursor.urlEncode())
            }
        }

        fun buildProfileUrl(handle: String): String =
            "https://api.x.com/2/users/by/username/${normalizeHandle(handle).urlEncode()}?user.fields=name,username"
    }
}

class TwitterClientException(
    override val clientError: ClientError,
) : RuntimeException(clientError.toString()), ClientFailure

@Serializable
private data class SearchRecentTweetsResponse(
    val data: List<TwitterTweet>? = null,
    val includes: TwitterIncludes? = null,
    val meta: TwitterMeta? = null,
)

@Serializable
private data class TwitterIncludes(
    val users: List<TwitterUser> = emptyList(),
)

@Serializable
private data class TwitterMeta(
    @SerialName("next_token") val nextToken: String? = null,
)

@Serializable
private data class TwitterTweet(
    val id: String,
    val text: String,
    @SerialName("author_id") val authorId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
private data class TwitterUser(
    val id: String,
    val name: String,
    val username: String,
)

@Serializable
private data class GetUserByUsernameResponse(
    val data: TwitterUser? = null,
)

@Serializable
private data class TwitterErrorResponse(
    val title: String? = null,
    val detail: String? = null,
)

private fun TwitterTweet.toFeedItem(
    sourceUser: String,
    author: TwitterUser?,
): FeedItem =
    FeedItem(
        itemId = id,
        platformId = PlatformId.Twitter,
        source = FeedSource(
            platformId = PlatformId.Twitter,
            sourceId = sourceUser,
            displayName = sourceUser,
        ),
        authorName = author?.name ?: normalizeHandle(sourceUser),
        title = null,
        body = text,
        permalink = author?.username?.let { "https://twitter.com/$it/status/$id" },
        publishedAtEpochMillis = parseTimestamp(createdAt) ?: 0L,
        seenState = SeenState.Unseen,
    )

private fun NetworkResponse.bodyOrThrow(): String =
    if (statusCode in 200..299) {
        body
    } else {
        throw TwitterClientException(
            when (statusCode) {
                401, 403 -> ClientError.AuthenticationError(errorMessage())
                429 -> ClientError.RateLimitError(retryAfterMillis = retryAfterMillis())
                in 400..499 -> ClientError.PermanentFailure(errorMessage())
                else -> ClientError.NetworkError(errorMessage())
            },
        )
    }

private fun NetworkResponse.retryAfterMillis(): Long? =
    headers["retry-after"]?.trim()?.toLongOrNull()?.times(1000)

private fun NetworkResponse.errorMessage(): String {
    val fallback = "HTTP $statusCode"
    val parsed = runCatching { twitterJson.decodeFromString<TwitterErrorResponse>(body) }.getOrNull()
    return parsed?.detail ?: parsed?.title ?: fallback
}

private fun Throwable.asTwitterException(defaultMessage: String): TwitterClientException =
    when (this) {
        is TwitterClientException -> this
        else -> TwitterClientException(ClientError.NetworkError(message ?: defaultMessage))
    }

private fun Throwable.asTwitterParsingException(): TwitterClientException =
    when (this) {
        is TwitterClientException -> this
        else -> TwitterClientException(ClientError.ParsingError(message))
    }

private fun parseTimestamp(value: String?): Long? =
    value?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }

private fun normalizeHandle(value: String): String = value.trim().removePrefix("@")
private fun normalizedHandleKey(value: String): String = normalizeHandle(value).lowercase()

private fun String.urlEncode(): String = encodeURLParameter()
