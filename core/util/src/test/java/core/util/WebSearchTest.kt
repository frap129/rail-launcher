package core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val GOOGLE_TEMPLATE: String = "https://www.google.com/search?q=%s"

class WebSearchTest {
    @Test
    fun `keeps unreserved characters`() {
        val inputQuery: String = "Az09-._~"

        val actualEncoded: String = encodeUrlComponent(inputQuery)

        assertEquals(inputQuery, actualEncoded)
    }

    @Test
    fun `encodes spaces`() {
        assertEquals("rail%20launcher", encodeUrlComponent("rail launcher"))
    }

    @Test
    fun `encodes apostrophes`() {
        assertEquals("it%27s", encodeUrlComponent("it's"))
    }

    @Test
    fun `encodes the characters that could change the URL`() {
        assertEquals("a%2Bb%26c%3Dd%3F%23%25%2F%3A", encodeUrlComponent("a+b&c=d?#%/:"))
    }

    @Test
    fun `encodes non-Latin text as UTF-8`() {
        assertEquals("%E6%9D%B1%E4%BA%AC", encodeUrlComponent("東京"))
        assertEquals("caf%C3%A9", encodeUrlComponent("café"))
    }

    @Test
    fun `detects a bare domain as a typed URL`() {
        assertEquals("https://example.com", typedUrl("example.com"))
        assertEquals("https://sub.example.co.uk", typedUrl("sub.example.co.uk"))
    }

    @Test
    fun `keeps the path and query of a typed URL`() {
        assertEquals("https://example.com/path?q=1", typedUrl("example.com/path?q=1"))
    }

    @Test
    fun `detects a host with a port as a typed URL`() {
        assertEquals("https://example.com:8080/path", typedUrl("example.com:8080/path"))
        assertEquals("https://localhost:8080", typedUrl("localhost:8080"))
    }

    @Test
    fun `detects an IP address or localhost as a typed URL`() {
        assertEquals("https://192.168.1.1", typedUrl("192.168.1.1"))
        assertEquals("https://192.168.1.1:8080", typedUrl("192.168.1.1:8080"))
        assertEquals("https://localhost", typedUrl("localhost"))
    }

    @Test
    fun `rejects an invalid IP address or port`() {
        assertNull(typedUrl("192.168.1.256"))
        assertNull(typedUrl("192.168.1"))
        assertNull(typedUrl("192.168.1.1:0"))
        assertNull(typedUrl("192.168.1.1:65536"))
        assertNull(typedUrl("example.com:"))
        assertNull(typedUrl("example.com:port"))
    }

    @Test
    fun `rejects calculator expressions and decimals`() {
        assertNull(typedUrl("1.5"))
        assertNull(typedUrl("3.14+2"))
        assertNull(typedUrl("1.2.3"))
        assertNull(typedUrl("12*7"))
    }

    @Test
    fun `keeps an explicit HTTP or HTTPS URL with a lowercase scheme`() {
        assertEquals("https://example.com", typedUrl("https://example.com"))
        assertEquals("http://example.com", typedUrl("http://example.com"))
        assertEquals("https://example.com", typedUrl("HTTPS://example.com"))
    }

    @Test
    fun `rejects a query that is not a web address`() {
        assertNull(typedUrl(""))
        assertNull(typedUrl("not a url"))
        assertNull(typedUrl("99"))
        assertNull(typedUrl(".com"))
        assertNull(typedUrl("example"))
        assertNull(typedUrl("ftp://example.com"))
        assertNull(typedUrl("https://"))
    }

    @Test
    fun `rejects an email address as a typed URL`() {
        assertNull(typedUrl("someone@example.com"))
    }

    @Test
    fun `accepts an HTTP or HTTPS template whose placeholder is after the host`() {
        assertEquals(true, isValidSearchTemplate(GOOGLE_TEMPLATE))
        assertEquals(true, isValidSearchTemplate("http://example.com/?q=%s"))
        assertEquals(true, isValidSearchTemplate("https://example.com/%s"))
        assertEquals(true, isValidSearchTemplate("https://example.com#%s"))
    }

    @Test
    fun `rejects a template without the placeholder`() {
        assertEquals(false, isValidSearchTemplate("https://example.com/search?q="))
        assertEquals(false, isValidSearchTemplate(""))
    }

    @Test
    fun `rejects a template that is not an HTTP or HTTPS URL`() {
        assertEquals(false, isValidSearchTemplate("ftp://example.com/%s"))
        assertEquals(false, isValidSearchTemplate("example.com/%s"))
        assertEquals(false, isValidSearchTemplate("javascript:%s"))
    }

    @Test
    fun `rejects a template whose placeholder could change the host`() {
        assertEquals(false, isValidSearchTemplate("https://%s.example.com/"))
        assertEquals(false, isValidSearchTemplate("https://example.com%s/"))
        assertEquals(false, isValidSearchTemplate("https:///%s"))
    }

    @Test
    fun `substitutes the encoded query into a template`() {
        val actualUrl: String = substituteSearchTemplate(template = GOOGLE_TEMPLATE, query = "it's a café")

        assertEquals("https://www.google.com/search?q=it%27s%20a%20caf%C3%A9", actualUrl)
    }

    @Test
    fun `builds a search URL only from a valid template`() {
        val actualUrl: String? = searchUrlFor(template = GOOGLE_TEMPLATE, query = "a&b")

        assertEquals("https://www.google.com/search?q=a%26b", actualUrl)
        assertNull(searchUrlFor(template = "https://example.com/search", query = "a&b"))
    }
}
