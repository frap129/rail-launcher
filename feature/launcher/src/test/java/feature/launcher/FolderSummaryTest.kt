package feature.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderSummaryTest {
    @Test
    fun `separates the names with commas in item order`() {
        val actualLine: String? = folderSummaryLine(listOf("WhatsApp", "Signal"))

        assertEquals("WhatsApp, Signal", actualLine)
    }

    @Test
    fun `keeps a line at the limit unellipsized`() {
        val actualLine: String? = folderSummaryLine(listOf("WhatsApp", "Signal", "Telegram", "Messages"))

        assertEquals("WhatsApp, Signal, Telegram, Messages", actualLine)
    }

    @Test
    fun `ellipsizes past the maximum length`() {
        val actualLine: String? = folderSummaryLine(listOf("WhatsApp", "Signal", "Telegram", "Messages", "Discord", "Slack"))

        assertEquals("WhatsApp, Signal, Telegram, Messages, Discord, S…", actualLine)
    }

    @Test
    fun `has no line without names`() {
        val actualLine: String? = folderSummaryLine(emptyList())

        assertNull(actualLine)
    }
}
