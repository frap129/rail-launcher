package feature.launcher

private const val FOLDER_SUMMARY_MAX_LENGTH: Int = 48
private const val FOLDER_SUMMARY_SEPARATOR: String = ", "
private const val ELLIPSIS: String = "…"

/**
 * The Folder row's second line: the names in item order, comma separated, ellipsized with
 * "…" past [FOLDER_SUMMARY_MAX_LENGTH]; null when there are no names.
 */
fun folderSummaryLine(names: List<String>): String? {
    val joined: String = names.joinToString(separator = FOLDER_SUMMARY_SEPARATOR)
    if (joined.isEmpty()) {
        return null
    }
    return if (joined.length <= FOLDER_SUMMARY_MAX_LENGTH) {
        joined
    } else {
        joined.take(FOLDER_SUMMARY_MAX_LENGTH).trimEnd() + ELLIPSIS
    }
}
