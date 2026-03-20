package com.franklinharper.social.media.client.client.bluesky

import com.franklinharper.social.media.client.client.FollowingImportClient
import com.franklinharper.social.media.client.client.PasswordAuthClient
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Instant

class BlueskyClient(
    private val sessionProvider: suspend () -> AccountSession? = { null },
    private val fetchAuthorFeed: suspend (String, String?) -> String = { actor, cursor ->
        defaultFetcher(
            url = buildString {
                append("https://public.api.bsky.app/xrpc/app.bsky.feed.getAuthorFeed?actor=")
                append(actor.urlEncode())
                append("&limit=30")
                if (cursor != null) {
                    append("&cursor=")
                    append(cursor.urlEncode())
                }
            },
            authorization = null,
        )
    },
    private val createSession: suspend (String, String) -> String = { identifier, password ->
        defaultJsonPost(
            url = "https://bsky.social/xrpc/com.atproto.server.createSession",
            body = """{"identifier":${identifier.toJsonString()},"password":${password.toJsonString()}}""",
        )
    },
    private val fetchFollows: suspend (String, String?) -> String = { actor, cursor ->
        defaultFetcher(
            url = buildString {
                append("https://public.api.bsky.app/xrpc/app.bsky.graph.getFollows?actor=")
                append(actor.urlEncode())
                append("&limit=100")
                if (cursor != null) {
                    append("&cursor=")
                    append(cursor.urlEncode())
                }
            },
            authorization = null,
        )
    },
) : SocialPlatformClient, PasswordAuthClient, FollowingImportClient {
    override val id: PlatformId = PlatformId.Bluesky
    override val displayName: String = "Bluesky"

    override suspend fun sessionState(): SessionState =
        sessionProvider()?.let(SessionState::SignedIn) ?: SessionState.SignedOut

    override suspend fun loadProfile(accountId: String): SocialProfile =
        SocialProfile(accountId = accountId, displayName = accountId, handle = accountId)

    override suspend fun signIn(identifier: String, password: String): AccountSession {
        val payload = try {
            createSession(identifier, password)
        } catch (error: Throwable) {
            throw error.asBlueskyException(defaultMessage = "Unable to sign in to Bluesky")
        }
        return try {
            val response = json.decodeFromString<CreateSessionResponse>(payload)
            AccountSession(
                accountId = response.did,
                accessToken = response.accessJwt,
                refreshToken = response.refreshJwt,
            )
        } catch (error: Throwable) {
            throw BlueskyClientException(ClientError.ParsingError(error.message))
        }
    }

    override suspend fun loadFollowedProfiles(accountId: String): List<SocialProfile> {
        val profiles = mutableListOf<SocialProfile>()
        var cursor: String? = null
        do {
            val payload = try {
                fetchFollows(accountId, cursor)
            } catch (error: Throwable) {
                throw error.asBlueskyException(defaultMessage = "Unable to load followed accounts")
            }
            val response = try {
                json.decodeFromString<GetFollowsResponse>(payload)
            } catch (error: Throwable) {
                throw BlueskyClientException(ClientError.ParsingError(error.message))
            }
            profiles += response.follows.map { profile ->
                SocialProfile(
                    accountId = profile.did,
                    displayName = profile.displayName ?: profile.handle,
                    handle = profile.handle,
                )
            }
            cursor = response.cursor
        } while (cursor != null)
        return profiles
    }

    override suspend fun loadFeed(query: FeedQuery, cursor: FeedCursor?): FeedPage {
        val socialQuery = query as? FeedQuery.SocialUsers
            ?: throw BlueskyClientException(ClientError.PermanentFailure("BlueskyClient requires SocialUsers query"))
        val items = socialQuery.users.flatMap { user ->
            val payload = try {
                fetchAuthorFeed(user, cursor?.value ?: socialQuery.cursor?.value)
            } catch (error: Throwable) {
                throw error.asBlueskyException(defaultMessage = "Unable to load Bluesky feed")
            }
            try {
                val response = json.decodeFromString<GetAuthorFeedResponse>(payload)
                response.feed.mapNotNull { entry ->
                    entry.post?.toFeedItem(sourceUser = user)
                }
            } catch (error: Throwable) {
                throw BlueskyClientException(ClientError.ParsingError(error.message))
            }
        }.sortedByDescending(FeedItem::publishedAtEpochMillis)
        return FeedPage(items = items, nextCursor = null)
    }

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        val httpClient: HttpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

        suspend fun defaultFetcher(
            url: String,
            authorization: String?,
        ): String = withContext(Dispatchers.IO) {
            val requestBuilder = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .header("User-Agent", "social-media-client/0.1")
            if (authorization != null) {
                requestBuilder.header("Authorization", authorization)
            }
            val response = httpClient.send(
                requestBuilder.GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            response.bodyOrThrow()
        }

        suspend fun defaultJsonPost(url: String, body: String): String = withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "social-media-client/0.1")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            response.bodyOrThrow()
        }
    }
}

class BlueskyClientException(
    override val clientError: ClientError,
) : RuntimeException(clientError.toString()), ClientFailure

@Serializable
private data class CreateSessionResponse(
    val did: String,
    val handle: String,
    val accessJwt: String,
    val refreshJwt: String,
)

@Serializable
private data class GetAuthorFeedResponse(
    val feed: List<FeedEntry> = emptyList(),
)

@Serializable
private data class GetFollowsResponse(
    val follows: List<ActorProfileView> = emptyList(),
    val cursor: String? = null,
)

@Serializable
private data class ActorProfileView(
    val did: String,
    val handle: String,
    val displayName: String? = null,
)

@Serializable
private data class FeedEntry(
    val post: PostView? = null,
)

@Serializable
private data class PostView(
    val uri: String,
    val author: AuthorView,
    val record: PostRecord? = null,
    val indexedAt: String? = null,
)

@Serializable
private data class AuthorView(
    val did: String,
    val handle: String,
    val displayName: String? = null,
)

@Serializable
private data class PostRecord(
    val text: String? = null,
    @SerialName("createdAt") val createdAt: String? = null,
)

private fun PostView.toFeedItem(sourceUser: String): FeedItem? {
    val rkey = uri.substringAfterLast('/', missingDelimiterValue = "")
    if (rkey.isBlank()) return null
    return FeedItem(
        itemId = uri,
        platformId = PlatformId.Bluesky,
        source = FeedSource(
            platformId = PlatformId.Bluesky,
            sourceId = sourceUser,
            displayName = sourceUser,
        ),
        authorName = author.displayName ?: author.handle,
        title = null,
        body = record?.text,
        permalink = "https://bsky.app/profile/${author.handle}/post/$rkey",
        publishedAtEpochMillis = parseTimestamp(record?.createdAt) ?: parseTimestamp(indexedAt) ?: 0L,
        seenState = SeenState.Unseen,
    )
}

private fun HttpResponse<String>.bodyOrThrow(): String =
    if (statusCode() in 200..299) {
        body()
    } else {
        throw BlueskyClientException(
            when (statusCode()) {
                401, 403 -> ClientError.AuthenticationError("HTTP ${statusCode()}")
                429 -> ClientError.RateLimitError()
                in 400..499 -> ClientError.PermanentFailure("HTTP ${statusCode()}")
                else -> ClientError.NetworkError("HTTP ${statusCode()}")
            },
        )
    }

private fun Throwable.asBlueskyException(defaultMessage: String): BlueskyClientException =
    when (this) {
        is BlueskyClientException -> this
        else -> BlueskyClientException(ClientError.NetworkError(message ?: defaultMessage))
    }

private fun parseTimestamp(value: String?): Long? =
    value?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8)

private fun String.toJsonString(): String =
    buildString(length + 2) {
        append('"')
        for (char in this@toJsonString) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }
