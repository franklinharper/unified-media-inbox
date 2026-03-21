package com.franklinharper.social.media.client.client.rss

import com.franklinharper.social.media.client.client.NetworkHttp
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
import kotlinx.datetime.Instant

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
                throw if (error is RssClientException) error else RssClientException(ClientError.NetworkError(error.message))
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
        val root = SimpleXmlParser(xml).parse()
        return when (root.localName.lowercase()) {
            "rss" -> parseRss(root, sourceUrl)
            "feed" -> parseAtom(root, sourceUrl)
            else -> throw RssClientException(ClientError.ParsingError("Unsupported feed format: ${root.name}"))
        }
    }

    private fun parseRss(root: XmlElement, sourceUrl: String): List<FeedItem> {
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
                authorName = item.childText("author") ?: item.childText("creator"),
                title = item.childText("title"),
                body = item.childText("description") ?: item.childText("encoded"),
                permalink = permalink,
                publishedAtEpochMillis = parseDate(item.childText("pubDate")) ?: 0L,
                seenState = SeenState.Unseen,
            )
        }
    }

    private fun parseAtom(root: XmlElement, sourceUrl: String): List<FeedItem> {
        val feedTitle = root.childText("title") ?: sourceUrl
        val source = FeedSource(
            platformId = PlatformId.Rss,
            sourceId = sourceUrl,
            displayName = feedTitle,
        )
        return root.childElements("entry").mapIndexed { index, entry ->
            val permalink = entry.childElements("link")
                .firstOrNull { link -> link.attributes["rel"].orEmpty().ifBlank { "alternate" } == "alternate" }
                ?.attributes
                ?.get("href")
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
        return parseIsoInstant(value) ?: parseRfc1123Instant(value)
    }

    private companion object {
        suspend fun defaultFetcher(url: String): String {
            val response = NetworkHttp.get(url)
            if (response.statusCode !in 200..299) {
                throw RssClientException(ClientError.NetworkError("HTTP ${response.statusCode}"))
            }
            return response.body
        }
    }
}

class RssClientException(
    override val clientError: ClientError,
) : RuntimeException(clientError.toString()), ClientFailure

private data class XmlElement(
    val name: String,
    val attributes: Map<String, String>,
    val children: MutableList<XmlElement> = mutableListOf(),
    val textFragments: MutableList<String> = mutableListOf(),
) {
    val localName: String
        get() = name.substringAfter(':')

    fun childElements(localName: String): List<XmlElement> =
        children.filter { it.localName == localName }

    fun childText(localName: String): String? =
        childElements(localName).firstOrNull()?.text()?.ifBlank { null }

    fun text(): String = textFragments.joinToString("").trim()
}

private class SimpleXmlParser(
    private val input: String,
) {
    fun parse(): XmlElement {
        val stack = mutableListOf<XmlElement>()
        var root: XmlElement? = null
        var index = 0
        while (index < input.length) {
            if (input[index] != '<') {
                val nextTag = input.indexOf('<', startIndex = index).let { if (it == -1) input.length else it }
                if (stack.isNotEmpty()) {
                    val text = decodeXml(input.substring(index, nextTag))
                    if (text.isNotEmpty()) {
                        stack.last().textFragments += text
                    }
                }
                index = nextTag
                continue
            }
            when {
                input.startsWith("<?", index) -> {
                    index = input.indexOf("?>", startIndex = index).takeIf { it >= 0 }?.plus(2)
                        ?: throw RssClientException(ClientError.ParsingError("Unterminated XML declaration"))
                }
                input.startsWith("<!--", index) -> {
                    index = input.indexOf("-->", startIndex = index).takeIf { it >= 0 }?.plus(3)
                        ?: throw RssClientException(ClientError.ParsingError("Unterminated XML comment"))
                }
                input.startsWith("<![CDATA[", index) -> {
                    val end = input.indexOf("]]>", startIndex = index)
                    if (end < 0) throw RssClientException(ClientError.ParsingError("Unterminated CDATA section"))
                    if (stack.isNotEmpty()) {
                        stack.last().textFragments += input.substring(index + 9, end)
                    }
                    index = end + 3
                }
                input.startsWith("</", index) -> {
                    val end = input.indexOf('>', startIndex = index)
                    if (end < 0) throw RssClientException(ClientError.ParsingError("Unterminated closing tag"))
                    val name = input.substring(index + 2, end).trim().substringBefore(' ')
                    val current = stack.removeLastOrNull()
                        ?: throw RssClientException(ClientError.ParsingError("Unexpected closing tag"))
                    if (current.localName != name.substringAfter(':')) {
                        throw RssClientException(ClientError.ParsingError("Mismatched closing tag"))
                    }
                    if (stack.isEmpty()) {
                        root = current
                    } else {
                        stack.last().children += current
                    }
                    index = end + 1
                }
                else -> {
                    val end = findTagEnd(index + 1)
                    val rawContent = input.substring(index + 1, end)
                    val selfClosing = rawContent.trimEnd().endsWith("/")
                    val tagContent = rawContent.removeSuffix("/").trim()
                    val name = tagContent.substringBeforeAny(' ', '\t', '\n', '\r')
                    val attributes = parseAttributes(tagContent.removePrefix(name).trim())
                    val element = XmlElement(name = name, attributes = attributes)
                    if (selfClosing) {
                        if (stack.isEmpty()) {
                            root = element
                        } else {
                            stack.last().children += element
                        }
                    } else {
                        stack += element
                    }
                    index = end + 1
                }
            }
        }
        if (stack.isNotEmpty()) {
            throw RssClientException(ClientError.ParsingError("Unterminated XML element"))
        }
        return root ?: throw RssClientException(ClientError.ParsingError("Missing root element"))
    }

    private fun findTagEnd(startIndex: Int): Int {
        var index = startIndex
        var quote: Char? = null
        while (index < input.length) {
            val char = input[index]
            when {
                quote != null && char == quote -> quote = null
                quote == null && (char == '"' || char == '\'') -> quote = char
                quote == null && char == '>' -> return index
            }
            index++
        }
        throw RssClientException(ClientError.ParsingError("Unterminated tag"))
    }
}

private fun parseAttributes(input: String): Map<String, String> {
    if (input.isBlank()) return emptyMap()
    val attributes = mutableMapOf<String, String>()
    var index = 0
    while (index < input.length) {
        while (index < input.length && input[index].isWhitespace()) index++
        if (index >= input.length) break
        val nameStart = index
        while (index < input.length && !input[index].isWhitespace() && input[index] != '=') index++
        val name = input.substring(nameStart, index)
        while (index < input.length && input[index].isWhitespace()) index++
        if (index >= input.length || input[index] != '=') {
            attributes[name.substringAfter(':')] = ""
            continue
        }
        index++
        while (index < input.length && input[index].isWhitespace()) index++
        if (index >= input.length) break
        val quote = input[index]
        val value = if (quote == '"' || quote == '\'') {
            index++
            val valueStart = index
            while (index < input.length && input[index] != quote) index++
            val raw = input.substring(valueStart, index)
            index++
            raw
        } else {
            val valueStart = index
            while (index < input.length && !input[index].isWhitespace()) index++
            input.substring(valueStart, index)
        }
        attributes[name.substringAfter(':')] = decodeXml(value)
    }
    return attributes
}

private fun decodeXml(value: String): String =
    value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

private fun String.substringBeforeAny(vararg delimiters: Char): String {
    val indexes = delimiters.map { delimiter -> indexOf(delimiter).takeIf { it >= 0 } ?: length }
    return substring(0, indexes.minOrNull() ?: length)
}

private fun parseIsoInstant(value: String): Long? =
    runCatching { Instant.parse(value.trim()).toEpochMilliseconds() }.getOrNull()

private fun parseRfc1123Instant(value: String): Long? {
    val match = RFC_1123_REGEX.matchEntire(value.trim()) ?: return null
    val month = MONTHS[match.groupValues[2].lowercase()] ?: return null
    val timezone = match.groupValues[6].normalizeOffset() ?: return null
    val isoValue = buildString {
        append(match.groupValues[3])
        append('-')
        append(month)
        append('-')
        append(match.groupValues[1].padStart(2, '0'))
        append('T')
        append(match.groupValues[4])
        append(':')
        append(match.groupValues[5])
        append(timezone)
    }
    return parseIsoInstant(isoValue)
}

private fun String.normalizeOffset(): String? = when (this.uppercase()) {
    "GMT", "UTC", "UT", "Z" -> "Z"
    else -> if (matches(Regex("[+-]\\d{4}"))) "${substring(0, 3)}:${substring(3, 5)}" else null
}

private val RFC_1123_REGEX = Regex(
    pattern = """(?:[A-Za-z]{3},\s+)?(\d{1,2})\s+([A-Za-z]{3})\s+(\d{4})\s+(\d{2}:\d{2}):(\d{2})\s+([A-Za-z]+|[+-]\d{4})""",
)

private val MONTHS = mapOf(
    "jan" to "01",
    "feb" to "02",
    "mar" to "03",
    "apr" to "04",
    "may" to "05",
    "jun" to "06",
    "jul" to "07",
    "aug" to "08",
    "sep" to "09",
    "oct" to "10",
    "nov" to "11",
    "dec" to "12",
)
