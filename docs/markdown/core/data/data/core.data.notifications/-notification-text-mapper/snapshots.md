//[data](../../../index.md)/[core.data.notifications](../index.md)/[NotificationTextMapper](index.md)/[snapshots](snapshots.md)

# snapshots

[main]\
fun [snapshots](snapshots.md)(records: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[NotificationRecord](../-notification-record/index.md)&gt;, ownPackageName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[NotificationSnapshot](../-notification-snapshot/index.md)&gt;

Builds the visible [NotificationSnapshot](../-notification-snapshot/index.md)s: Rail's own notifications and group summaries whose children are visible are skipped, blank notifications are dropped, and the rest are ordered newest first.
