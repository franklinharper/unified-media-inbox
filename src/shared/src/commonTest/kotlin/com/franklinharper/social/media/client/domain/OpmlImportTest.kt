package com.franklinharper.social.media.client.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class OpmlImportTest {

    @Test
    fun `extracts deduplicated RSS feed urls from opml`() {
        val urls = extractRssFeedUrlsFromOpml(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="1.0">
              <body>
                <outline text="Tech">
                  <outline text="HN" type="rss" xmlUrl="https://hnrss.org/newest"/>
                  <outline text="Kotlin" type="rss" xmlUrl='https://example.com/feed?topic=kotlin&amp;sort=new'/>
                  <outline text="Duplicate" type="rss" xmlUrl="https://hnrss.org/newest"/>
                </outline>
              </body>
            </opml>
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                "https://hnrss.org/newest",
                "https://example.com/feed?topic=kotlin&sort=new",
            ),
            urls,
        )
    }

    @Test
    fun `returns empty list when opml has no rss outlines`() {
        val urls = extractRssFeedUrlsFromOpml(
            """
            <opml version="1.0">
              <body>
                <outline text="Folder"/>
              </body>
            </opml>
            """.trimIndent(),
        )

        assertEquals(emptyList(), urls)
    }
}
