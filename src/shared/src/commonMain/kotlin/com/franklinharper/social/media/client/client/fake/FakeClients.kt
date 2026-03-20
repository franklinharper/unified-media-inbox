package com.franklinharper.social.media.client.client.fake

import com.franklinharper.social.media.client.client.SocialPlatformClient
import com.franklinharper.social.media.client.client.PasswordAuthClient
import com.franklinharper.social.media.client.domain.AccountSession
import com.franklinharper.social.media.client.domain.ClientError
import com.franklinharper.social.media.client.domain.ClientFailure
import com.franklinharper.social.media.client.domain.FeedCursor
import com.franklinharper.social.media.client.domain.FeedItem
import com.franklinharper.social.media.client.domain.FeedPage
import com.franklinharper.social.media.client.domain.FeedQuery
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SessionState
import com.franklinharper.social.media.client.domain.SocialProfile

class FakeClientException(
    override val clientError: ClientError,
) : RuntimeException(clientError.toString()), ClientFailure

open class FakeSocialPlatformClient(
    override val id: PlatformId,
    override val displayName: String,
    private val sessionStateProvider: suspend () -> SessionState = { SessionState.NotRequired },
    private val profileProvider: suspend (String) -> SocialProfile = { accountId ->
        SocialProfile(accountId = accountId, displayName = accountId)
    },
    private val feedProvider: suspend (FeedQuery, FeedCursor?) -> FeedPage,
) : SocialPlatformClient {
    override suspend fun sessionState(): SessionState = sessionStateProvider()

    override suspend fun loadProfile(accountId: String): SocialProfile = profileProvider(accountId)

    override suspend fun loadFeed(query: FeedQuery, cursor: FeedCursor?): FeedPage = feedProvider(query, cursor)
}

class FakeRssClient(
    private val itemsByUrl: Map<String, List<FeedItem>>,
    private val errorsByUrl: Map<String, ClientError> = emptyMap(),
) : FakeSocialPlatformClient(
    id = PlatformId.Rss,
    displayName = "Fake RSS",
    feedProvider = { query, _ ->
        val rssQuery = query as? FeedQuery.RssFeeds ?: error("FakeRssClient only supports RssFeeds queries")
        val items = rssQuery.urls.flatMap { url ->
            errorsByUrl[url]?.let { throw FakeClientException(it) }
            itemsByUrl[url].orEmpty()
        }.sortedByDescending(FeedItem::publishedAtEpochMillis)
        FeedPage(items = items, nextCursor = null)
    },
)

class FakeBlueskyClient(
    private val itemsByUser: Map<String, List<FeedItem>>,
    private val errorsByUser: Map<String, ClientError> = emptyMap(),
    private val sessionsByIdentifier: Map<String, AccountSession> = emptyMap(),
    sessionStateProvider: suspend () -> SessionState = { SessionState.SignedOut },
) : FakeSocialPlatformClient(
    id = PlatformId.Bluesky,
    displayName = "Fake Bluesky",
    sessionStateProvider = sessionStateProvider,
    feedProvider = { query, _ ->
        val socialQuery = query as? FeedQuery.SocialUsers ?: error("FakeBlueskyClient only supports SocialUsers queries")
        val items = socialQuery.users.flatMap { user ->
            errorsByUser[user]?.let { throw FakeClientException(it) }
            itemsByUser[user].orEmpty()
        }.sortedByDescending(FeedItem::publishedAtEpochMillis)
        FeedPage(items = items, nextCursor = null)
    },
), PasswordAuthClient {
    override suspend fun signIn(identifier: String, password: String): AccountSession =
        sessionsByIdentifier[identifier]
            ?: throw FakeClientException(ClientError.AuthenticationError("invalid credentials"))
}

class FakeTwitterClient(
    private val itemsByUser: Map<String, List<FeedItem>>,
    private val errorsByUser: Map<String, ClientError> = emptyMap(),
    sessionStateProvider: suspend () -> SessionState = { SessionState.SignedOut },
) : FakeSocialPlatformClient(
    id = PlatformId.Twitter,
    displayName = "Fake Twitter",
    sessionStateProvider = sessionStateProvider,
    feedProvider = { query, _ ->
        val socialQuery = query as? FeedQuery.SocialUsers ?: error("FakeTwitterClient only supports SocialUsers queries")
        val items = socialQuery.users.flatMap { user ->
            errorsByUser[user]?.let { throw FakeClientException(it) }
            itemsByUser[user].orEmpty()
        }.sortedByDescending(FeedItem::publishedAtEpochMillis)
        FeedPage(items = items, nextCursor = null)
    },
)
