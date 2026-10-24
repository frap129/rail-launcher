package core.data.shortcuts

import android.os.UserHandle
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val KEY_PREFIX: String = "shortcut:"
private const val KEY_SEPARATOR: String = ":"
private const val USER_HANDLE_REGEX: String = """UserHandle\{(\d+)}"""
private const val USER_ID_OFFSET: Int = 100_000

/** The identity of one shortcut published by an app in a user profile. */
data class ShortcutKey(val packageName: String, val id: String, val profile: UserHandle) {
    /** The stored form: `shortcut:<packageName>:<urlEncoded(id)>:<profile>`. */
    fun format(): String =
        "$KEY_PREFIX$packageName$KEY_SEPARATOR${URLEncoder.encode(id, StandardCharsets.UTF_8.name())}$KEY_SEPARATOR$profile"

    companion object {
        /** Reads a [format]ted key, or null when [key] is malformed. */
        fun parse(key: String): ShortcutKey? {
            if (!key.startsWith(KEY_PREFIX)) {
                return null
            }
            val parts: List<String> = key.removePrefix(KEY_PREFIX).split(KEY_SEPARATOR, limit = 3)
            if (parts.size != 3) {
                return null
            }
            val packageName: String = parts[0]
            val id: String = decodeId(parts[1]) ?: return null
            val profile: UserHandle = parseProfile(parts[2]) ?: return null
            if (packageName.isBlank() || id.isEmpty()) {
                return null
            }
            return ShortcutKey(packageName = packageName, id = id, profile = profile)
        }
        private fun decodeId(encodedId: String): String? = runCatching {
            URLDecoder.decode(encodedId, StandardCharsets.UTF_8.name())
        }.getOrNull()
        private fun parseProfile(value: String): UserHandle? {
            val userId: Int = Regex(USER_HANDLE_REGEX).matchEntire(value)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            return UserHandle.getUserHandleForUid(userId * USER_ID_OFFSET)
        }
    }
}
