//[data](../../index.md)/[core.data.launchables](index.md)/[toAppListLaunchables](to-app-list-launchables.md)

# toAppListLaunchables

[main]\
fun [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[LaunchableGroup](../core.data.launchables.model/-launchable-group/index.md)&gt;.[toAppListLaunchables](to-app-list-launchables.md)(): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[Launchable](../core.data.launchables.model/-launchable/index.md)&gt;

Flattens the App List groups into their Launchables in Rail order. The Folders group is skipped because every Folder is flat-listed from its record, whether or not that group shows it.
