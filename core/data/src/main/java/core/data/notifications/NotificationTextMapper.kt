package core.data.notifications

/** Turns notification records into the snapshots a Pop-up shows. */
object NotificationTextMapper {
    /**
     * Builds the visible [NotificationSnapshot]s: Rail's own notifications and group
     * summaries whose children are visible are skipped, blank notifications are dropped,
     * and the rest are ordered newest first.
     */
    fun snapshots(records: List<NotificationRecord>, ownPackageName: String): List<NotificationSnapshot> {
        val visibleRecords: List<NotificationRecord> = records
            .filter { record -> record.packageName != ownPackageName }
            .filter { record -> record.title.isNotBlank() || record.body().isNotBlank() }
        val visibleGroupKeys: Set<String> = visibleRecords
            .filterNot { record -> record.isGroupSummary }
            .mapNotNullTo(mutableSetOf()) { record -> record.groupKey }
        return visibleRecords
            .filterNot { record -> record.isGroupSummary && record.groupKey in visibleGroupKeys }
            .map { record -> record.toSnapshot() }
            .sortedByDescending { snapshot -> snapshot.postTime }
    }

    private fun NotificationRecord.body(): String = text.ifBlank { bigText }.ifBlank { lastMessage }

    private fun NotificationRecord.toSnapshot(): NotificationSnapshot = NotificationSnapshot(
        key = key,
        packageName = packageName,
        profile = profile,
        postTime = postTime,
        title = title,
        body = body(),
        isClearable = isClearable,
        hasBigText = bigText.isNotBlank(),
        hasActions = hasActions
    )
}
