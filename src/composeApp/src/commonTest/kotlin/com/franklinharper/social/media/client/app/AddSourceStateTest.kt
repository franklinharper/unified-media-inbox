package com.franklinharper.social.media.client.app

import com.franklinharper.social.media.client.domain.ConfiguredSource
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.repository.ConfiguredSourceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AddSourceStateTest {

    @Test
    fun `rss submission stores configured source`() = runTest {
        val fakeConfiguredSourceRepository = AddSourceFakeConfiguredSourceRepository()
        val state = AddSourceState(
            configuredSourceRepository = fakeConfiguredSourceRepository,
        )

        state.selectType(SourceType.Rss)
        state.addRssSource("https://hnrss.org/newest")

        assertEquals(SourceType.Rss, state.uiState.value.selectedType)
        assertFalse(state.uiState.value.isAdding)
        assertNull(state.uiState.value.addError)
        assertEquals(
            listOf<ConfiguredSource>(ConfiguredSource.RssFeed(url = "https://hnrss.org/newest")),
            fakeConfiguredSourceRepository.sources,
        )
    }

    @Test
    fun `bluesky submission stores configured source`() = runTest {
        val fakeConfiguredSourceRepository = AddSourceFakeConfiguredSourceRepository()
        val state = AddSourceState(
            configuredSourceRepository = fakeConfiguredSourceRepository,
        )

        state.selectType(SourceType.Bluesky)
        state.addBlueskySource("frank.bsky.social")

        assertEquals(SourceType.Bluesky, state.uiState.value.selectedType)
        assertFalse(state.uiState.value.isAdding)
        assertNull(state.uiState.value.addError)
        assertEquals(
            listOf<ConfiguredSource>(ConfiguredSource.SocialUser(PlatformId.Bluesky, "frank.bsky.social")),
            fakeConfiguredSourceRepository.sources,
        )
    }

    @Test
    fun `add source exposes add error and resets adding state`() = runTest {
        val state = AddSourceState(
            configuredSourceRepository = AddSourceFakeConfiguredSourceRepository(
                failure = IllegalStateException("duplicate"),
            ),
        )

        state.selectType(SourceType.Rss)
        state.addRssSource("https://hnrss.org/newest")

        assertFalse(state.uiState.value.isAdding)
        assertEquals("duplicate", state.uiState.value.addError)
    }

    @Test
    fun `add source rethrows cancellation`() = runTest {
        val state = AddSourceState(
            configuredSourceRepository = AddSourceFakeConfiguredSourceRepository(
                failure = CancellationException("cancelled"),
            ),
        )

        assertFailsWith<CancellationException> {
            state.addBlueskySource("frank.bsky.social")
        }
        assertFalse(state.uiState.value.isAdding)
    }
}

private class AddSourceFakeConfiguredSourceRepository(
    private val failure: Throwable? = null,
) : ConfiguredSourceRepository {
    val sources = mutableListOf<ConfiguredSource>()

    override suspend fun listSources(): List<ConfiguredSource> = sources.toList()

    override suspend fun addSource(source: ConfiguredSource) {
        failure?.let { throw it }
        sources += source
    }

    override suspend fun removeSource(source: ConfiguredSource) {
        error("Not used")
    }

    override suspend fun clearAll() {
        error("Not used")
    }
}
