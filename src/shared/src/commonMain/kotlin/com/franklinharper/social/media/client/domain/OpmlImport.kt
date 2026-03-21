package com.franklinharper.social.media.client.domain

private val outlineXmlUrlRegex = Regex(
    pattern = """<outline\b[^>]*\bxmlUrl\s*=\s*("([^"]*)"|'([^']*)')""",
    options = setOf(RegexOption.IGNORE_CASE),
)

private val xmlEntityRegex = Regex("""&(#x[0-9A-Fa-f]+|#\d+|amp|lt|gt|quot|apos);""")

fun extractRssFeedUrlsFromOpml(content: String): List<String> = buildList {
    val seen = linkedSetOf<String>()
    outlineXmlUrlRegex.findAll(content).forEach { match ->
        val rawValue = match.groups[2]?.value ?: match.groups[3]?.value.orEmpty()
        val decodedValue = decodeXmlEntities(rawValue).trim()
        if (decodedValue.isNotEmpty() && seen.add(decodedValue)) {
            add(decodedValue)
        }
    }
}

private fun decodeXmlEntities(value: String): String = xmlEntityRegex.replace(value) { match ->
    when (val entity = match.groupValues[1]) {
        "amp" -> "&"
        "lt" -> "<"
        "gt" -> ">"
        "quot" -> "\""
        "apos" -> "'"
        else -> decodeNumericXmlEntity(entity) ?: match.value
    }
}

private fun decodeNumericXmlEntity(entity: String): String? {
    val codePoint = when {
        entity.startsWith("#x", ignoreCase = true) -> entity.substring(2).toIntOrNull(16)
        entity.startsWith("#") -> entity.substring(1).toIntOrNull()
        else -> null
    } ?: return null

    return if (codePoint in 0..0xFFFF) {
        codePoint.toChar().toString()
    } else {
        null
    }
}
