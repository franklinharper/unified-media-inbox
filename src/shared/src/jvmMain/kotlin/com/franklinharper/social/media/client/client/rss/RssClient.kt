package com.franklinharper.social.media.client.client.rss

import com.franklinharper.social.media.client.client.SocialPlatformClient
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
import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class RssClient(
    private val fetcher: suspend (String) -> String = { url -> defaultFetcher(url) },
) : SocialPlatformClient {
    override val id: PlatformId = PlatformId.Rss
    override val displayName: String = "RSS"

    override suspend fun sessionState(): SessionState = SessionState.NotRequired

    override suspend fun loadProfile(accountId: String): SocialProfile =
        SocialProfile(accountId = accountId, displayName = accountId)

    override suspend fun loadFeed(query: FeedQuery, cursor: FeedCursor?): FeedPage {
        val rssQuery = query as? FeedQuery.RssFeeds
            ?: throw RssClientException(ClientError.PermanentFailure("RssClient requires RssFeeds query"))
        val items = rssQuery.urls.flatMap { url ->
            val xml = try {
                fetcher(url)
            } catch (error: Throwable) {
                throw RssClientException(ClientError.NetworkError(error.message))
            }
            try {
                parseFeed(xml, url)
            } catch (error: RssClientException) {
                throw error
            } catch (error: Throwable) {
                throw RssClientException(ClientError.ParsingError(error.message))
            }
        }.sortedByDescending(FeedItem::publishedAtEpochMillis)
        return FeedPage(items = items, nextCursor = null)
    }

    internal fun parseFeed(xml: String, sourceUrl: String): List<FeedItem> {
        val builder = documentBuilderFactory().newDocumentBuilder().apply {
            setErrorHandler(QuietXmlErrorHandler)
        }
        val document = builder
            .parse(InputSource(StringReader(xml)))
        document.documentElement.normalize()
        val root = document.documentElement
        return when (root.tagName.lowercase(Locale.US)) {
            "rss" -> parseRss(root, sourceUrl)
            "feed" -> parseAtom(root, sourceUrl)
            else -> throw RssClientException(ClientError.ParsingError("Unsupported feed format: ${root.tagName}"))
        }
    }

    private fun parseRss(root: Element, sourceUrl: String): List<FeedItem> {
        val channel = root.childElements("channel").firstOrNull()
            ?: throw RssClientException(ClientError.ParsingError("Missing channel element"))
        val channelTitle = channel.childText("title") ?: sourceUrl
        val source = FeedSource(
            platformId = PlatformId.Rss,
            sourceId = sourceUrl,
            displayName = channelTitle,
        )
        return channel.childElements("item").mapIndexed { index, item ->
            val permalink = item.childText("link")
            val guid = item.childText("guid")
            FeedItem(
                itemId = guid ?: permalink ?: "rss-$index-${item.childText("title") ?: "item"}",
                platformId = PlatformId.Rss,
                source = source,
                authorName = item.childText("author") ?: item.childTextNs("*", "creator"),
                title = item.childText("title"),
                body = item.childText("description") ?: item.childText("encoded"),
                permalink = permalink,
                publishedAtEpochMillis = parseDate(item.childText("pubDate")) ?: 0L,
                seenState = SeenState.Unseen,
            )
        }
    }

    private fun parseAtom(root: Element, sourceUrl: String): List<FeedItem> {
        val feedTitle = root.childText("title") ?: sourceUrl
        val source = FeedSource(
            platformId = PlatformId.Rss,
            sourceId = sourceUrl,
            displayName = feedTitle,
        )
        return root.childElements("entry").mapIndexed { index, entry ->
            val permalink = entry.childElements("link")
                .firstOrNull { link -> link.getAttribute("rel").ifBlank { "alternate" } == "alternate" }
                ?.getAttribute("href")
                ?.ifBlank { null }
            val updated = entry.childText("updated") ?: entry.childText("published")
            FeedItem(
                itemId = entry.childText("id") ?: permalink ?: "atom-$index-${entry.childText("title") ?: "entry"}",
                platformId = PlatformId.Rss,
                source = source,
                authorName = entry.childElements("author").firstOrNull()?.childText("name"),
                title = entry.childText("title"),
                body = entry.childText("summary") ?: entry.childText("content"),
                permalink = permalink,
                publishedAtEpochMillis = parseDate(updated) ?: 0L,
                seenState = SeenState.Unseen,
            )
        }
    }

    private fun parseDate(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .getOrNull()
    }

    private fun documentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }

    private companion object {
        val QuietXmlErrorHandler = object : DefaultHandler() {
            override fun warning(exception: SAXParseException) = Unit

            override fun error(exception: SAXParseException) {
                throw exception
            }

            override fun fatalError(exception: SAXParseException) {
                throw exception
            }
        }

        private val httpClient: HttpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

        suspend fun defaultFetcher(url: String): String = withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "social-media-client/0.1")
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                throw RssClientException(ClientError.NetworkError("HTTP ${response.statusCode()}"))
            }
            response.body()
        }
    }
}

class RssClientException(
    override val clientError: ClientError,
) : RuntimeException(clientError.toString()), ClientFailure

private fun Element.childElements(localName: String): List<Element> =
    buildList {
        val nodes = childNodes
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node is Element && node.localNameOrTag() == localName) {
                add(node)
            }
        }
    }

private fun Element.childText(localName: String): String? =
    childElements(localName).firstOrNull()?.textContent?.trim()?.ifBlank { null }

private fun Element.childTextNs(namespaceWildcard: String, localName: String): String? =
    buildList {
        val nodes = childNodes
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node is Element && node.localNameOrTag() == localName) {
                add(node)
            }
        }
    }.firstOrNull()?.textContent?.trim()?.ifBlank { null }

private fun Element.localNameOrTag(): String = localName ?: tagName.substringAfter(':')
