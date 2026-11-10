package core.util

/** The placeholder a Search engine template puts where the encoded query goes. */
const val SEARCH_TEMPLATE_PLACEHOLDER: String = "%s"

/** The characters an HTTP or HTTPS URL starts with. */
private val HTTP_URL_PREFIX: Regex = Regex("^https?://", RegexOption.IGNORE_CASE)

/** A bare host Search offers to open: one or more dot-separated labels and an alphabetic top-level domain. */
private val TYPED_URL_HOST: Regex = Regex("^([A-Za-z0-9-]+\\.)+[A-Za-z]{2,}$")

/** A valid IPv4 address: four dot-separated octets from 0 to 255. */
private val IPV4_HOST: Regex =
    Regex("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$")

/** The host Search opens without a scheme even though it has no dot. */
private const val LOCAL_HOST: String = "localhost"

/** The smallest valid port. */
private const val MINIMUM_PORT: Int = 1

/** The largest valid port. */
private const val MAXIMUM_PORT: Int = 65535

/** The separator between a bare host and its port. */
private const val PORT_SEPARATOR: Char = ':'

/** The characters that end a URL's host part. */
private val URL_HOST_TERMINATOR_CHARACTERS: CharArray = charArrayOf('/', '?', '#')

/** The hexadecimal digits used for percent-encoding. */
private const val HEXADECIMAL_DIGITS: String = "0123456789ABCDEF"

/** The highest value of a UTF-8 byte. */
private const val MAXIMUM_BYTE_VALUE: Int = 0xFF

/** The bits of a byte that the low hexadecimal digit holds. */
private const val LOW_HEXADECIMAL_BITS: Int = 0x0F

/** The bits a hexadecimal digit shifts by. */
private const val HEXADECIMAL_SHIFT: Int = 4

/** The characters RFC 3986 leaves unencoded in a URL component. */
private const val UNRESERVED_URL_CHARACTERS: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

/**
 * [value] percent-encoded as one URL component: every character outside the RFC 3986 unreserved
 * set is written as its UTF-8 bytes in uppercase hexadecimal, including spaces, `+`, `&` and
 * apostrophes. Apostrophes are encoded because DuckDuckGo reads an unencoded one as a query
 * separator.
 */
fun encodeUrlComponent(value: String): String {
    val encoded: StringBuilder = StringBuilder()
    value.toByteArray(Charsets.UTF_8).forEach { byte ->
        val code: Int = byte.toInt() and MAXIMUM_BYTE_VALUE
        val character: Char = code.toChar()
        if (character in UNRESERVED_URL_CHARACTERS) {
            encoded.append(character)
        } else {
            encoded.append('%')
                .append(HEXADECIMAL_DIGITS[code shr HEXADECIMAL_SHIFT])
                .append(HEXADECIMAL_DIGITS[code and LOW_HEXADECIMAL_BITS])
        }
    }
    return encoded.toString()
}

/**
 * The URL [query] should open when it looks like a web address, or null when it does not. A bare
 * host gets `https://`: a hostname such as `example.com`, an IPv4 address such as `192.168.1.1`
 * or `localhost`, each with an optional port. An explicit `http` or `https` URL keeps its host
 * and path with a lowercase scheme. A query with whitespace, an email address, a bare number or a
 * decimal, such as `1.5` or `3.14+2`, is not a web address.
 */
fun typedUrl(query: String): String? {
    val candidate: String = query.trim()
    if (candidate.isEmpty() || candidate.any { character -> character.isWhitespace() }) {
        return null
    }
    val schemePrefix: MatchResult? = HTTP_URL_PREFIX.matchAt(input = candidate, index = 0)
    val remainder: String = candidate.substring(schemePrefix?.value?.length ?: 0)
    val authority: String = remainder.takeWhile { character -> character !in URL_HOST_TERMINATOR_CHARACTERS }
    if (authority.isEmpty() || '@' in authority) {
        return null
    }
    if (schemePrefix != null) {
        return schemePrefix.value.lowercase() + remainder
    }
    if (PORT_SEPARATOR in authority && !isValidPort(port = authority.substringAfterLast(delimiter = PORT_SEPARATOR))) {
        return null
    }
    val host: String = authority.substringBeforeLast(delimiter = PORT_SEPARATOR, missingDelimiterValue = authority)
    return if (isTypedUrlHost(host = host)) "https://$candidate" else null
}

private fun isTypedUrlHost(host: String): Boolean =
    host.equals(other = LOCAL_HOST, ignoreCase = true) || IPV4_HOST.matches(host) || TYPED_URL_HOST.matches(host)

private fun isValidPort(port: String): Boolean {
    val number: Int = port.toIntOrNull() ?: return false
    return port.all { character -> character.isDigit() } && number in MINIMUM_PORT..MAXIMUM_PORT
}

/**
 * Whether [template] is a usable custom Search engine URL: an `http` or `https` URL with a host
 * and [SEARCH_TEMPLATE_PLACEHOLDER] after the host, so a query can never reach the scheme or the
 * host.
 */
fun isValidSearchTemplate(template: String): Boolean {
    val schemePrefix: MatchResult = HTTP_URL_PREFIX.matchAt(input = template, index = 0) ?: return false
    val placeholderIndex: Int = template.indexOf(SEARCH_TEMPLATE_PLACEHOLDER)
    if (placeholderIndex < 0) {
        return false
    }
    val hostEnd: Int = template.indexOfAny(URL_HOST_TERMINATOR_CHARACTERS, startIndex = schemePrefix.value.length)
        .takeIf { index -> index >= 0 }
        ?: template.length
    return hostEnd > schemePrefix.value.length && placeholderIndex >= hostEnd
}

/** [template] with every [SEARCH_TEMPLATE_PLACEHOLDER] replaced by [query] encoded for a URL. */
fun substituteSearchTemplate(template: String, query: String): String =
    template.replace(SEARCH_TEMPLATE_PLACEHOLDER, encodeUrlComponent(value = query))

/** The web search URL [template] builds for [query], or null when [template] is not valid. */
fun searchUrlFor(template: String, query: String): String? =
    if (isValidSearchTemplate(template = template)) substituteSearchTemplate(template = template, query = query) else null
