package com.franklinharper.social.media.client.app

import com.franklinharper.social.media.client.domain.ConfiguredSource
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.repository.DefaultFeedRepository
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class JvmAppContainerFactoryTest {

    @Test
    fun `jvm app container wires real file backed repositories and clients`() = runTest {
        val databaseFile = createTempFile(prefix = "compose-app", suffix = ".db").toFile().apply {
            deleteOnExit()
        }

        val container = createJvmAppContainer(databaseFile)

        assertIs<DefaultFeedRepository>(container.feedRepository)
        assertEquals(
            listOf(PlatformId.Rss, PlatformId.Bluesky, PlatformId.Twitter),
            container.clientRegistry.all().map { it.id },
        )

        val source = ConfiguredSource.RssFeed(url = "https://example.com/feed.xml")
        container.configuredSourceRepository.addSource(source)

        assertTrue(container.configuredSourceRepository.listSources().contains(source))
    }
}
