package com.franklinharper.social.media.client.app

import com.franklinharper.social.media.client.client.ClientRegistry
import com.franklinharper.social.media.client.client.bluesky.BlueskyClient
import com.franklinharper.social.media.client.client.rss.RssClient
import com.franklinharper.social.media.client.client.twitter.TwitterClient
import com.franklinharper.social.media.client.db.JvmDatabaseFactory
import com.franklinharper.social.media.client.domain.AccountSession
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SessionState
import com.franklinharper.social.media.client.repository.DefaultFeedRepository
import com.franklinharper.social.media.client.repository.LOCAL_OWNER_USER_ID
import com.franklinharper.social.media.client.repository.SqlDelightConfiguredSourceRepository
import com.franklinharper.social.media.client.repository.SqlDelightFeedCacheRepository
import com.franklinharper.social.media.client.repository.SqlDelightSeenItemRepository
import com.franklinharper.social.media.client.repository.SqlDelightSessionRepository
import com.franklinharper.social.media.client.repository.SqlDelightSourceErrorRepository
import com.franklinharper.social.media.client.remote.DefaultWebApiHttp
import com.franklinharper.social.media.client.sync.ServerSyncAuthenticatedSessionRepository
import com.franklinharper.social.media.client.sync.SqlDelightServerSyncSessionStore
import java.io.File

internal actual fun createAppContainer(): AppContainer = createJvmAppContainer()

internal fun createJvmAppContainer(
    databaseFile: File = defaultDatabaseFile(),
): AppContainer {
    val database = JvmDatabaseFactory.fileBacked(databaseFile)
    val configuredSourceRepository = SqlDelightConfiguredSourceRepository(database, LOCAL_OWNER_USER_ID)
    val seenItemRepository = SqlDelightSeenItemRepository(database, LOCAL_OWNER_USER_ID) { System.currentTimeMillis() }
    val sessionRepository = ServerSyncAuthenticatedSessionRepository(
        platformSessionRepository = SqlDelightSessionRepository(database),
        http = DefaultWebApiHttp(baseUrl = desktopApiBaseUrl()),
        serverSyncSessionStore = SqlDelightServerSyncSessionStore(database),
    )
    val feedCacheRepository = SqlDelightFeedCacheRepository(database, LOCAL_OWNER_USER_ID) { System.currentTimeMillis() }
    val sourceErrorRepository = SqlDelightSourceErrorRepository(database, LOCAL_OWNER_USER_ID)
    val clientRegistry = ClientRegistry(
        listOf(
            RssClient(),
            BlueskyClient(
                sessionProvider = {
                    when (val state = sessionRepository.getSessionState(PlatformId.Bluesky)) {
                        is SessionState.SignedIn -> state.session
                        else -> null
                    }
                },
            ),
            TwitterClient(
                sessionProvider = {
                    when (val state = sessionRepository.getSessionState(PlatformId.Twitter)) {
                        is SessionState.SignedIn -> state.session
                        else -> twitterSessionFromEnvironment()
                    }
                },
            ),
        ),
    )
    val feedRepository = DefaultFeedRepository(
        clientRegistry = clientRegistry,
        seenItemRepository = seenItemRepository,
        feedCacheRepository = feedCacheRepository,
        sourceErrorRepository = sourceErrorRepository,
        clock = { System.currentTimeMillis() },
    )

    return object : AppContainer {
        override val dependencies: AppDependencies = AppDependencies(
            clientRegistry = clientRegistry,
            configuredSourceRepository = configuredSourceRepository,
            sessionRepository = sessionRepository,
            seenItemRepository = seenItemRepository,
            feedCacheRepository = feedCacheRepository,
            sourceErrorRepository = sourceErrorRepository,
            feedRepository = feedRepository,
        )
    }
}

internal fun defaultDatabaseFile(
    osName: String = System.getProperty("os.name"),
    userHome: String = System.getProperty("user.home"),
    environment: Map<String, String> = System.getenv(),
): File = File(defaultDatabaseDirectory(osName, userHome, environment), "social-media-client.db")

private fun defaultDatabaseDirectory(
    osName: String,
    userHome: String,
    environment: Map<String, String>,
): File = when {
    osName.contains("Mac", ignoreCase = true) ->
        File(userHome, "Library/Application Support/Social Media Client")

    osName.contains("Windows", ignoreCase = true) ->
        environment["APPDATA"]?.takeIf(String::isNotBlank)?.let { File(it, "Social Media Client") }
            ?: File(userHome, "AppData/Roaming/Social Media Client")

    else ->
        environment["XDG_DATA_HOME"]?.takeIf(String::isNotBlank)?.let { File(it, "Social Media Client") }
            ?: File(userHome, ".local/share/Social Media Client")
}

private fun twitterSessionFromEnvironment(): AccountSession? {
    val token = sequenceOf("X_BEARER_TOKEN", "TWITTER_BEARER_TOKEN")
        .mapNotNull { key -> System.getenv(key)?.takeIf(String::isNotBlank) }
        .firstOrNull()
        ?: return null
    return AccountSession(
        accountId = "twitter-app",
        accessToken = token,
    )
}

private fun desktopApiBaseUrl(): String = "http://127.0.0.1:8080"
