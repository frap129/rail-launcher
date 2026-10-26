//[data](../../index.md)/[core.data.launchables.model](index.md)/[appListKey](app-list-key.md)

# appListKey

[main]\
fun [LaunchableGroup.Listed](-launchable-group/-listed/index.md).[appListKey](app-list-key.md)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)

A stable identity for the group in the App List. A name starting with a Rail label (e.g. &quot;⌂&quot; or &quot;◇&quot;) forms an Initial group that shares that label, so the key includes the group kind; the Folders key is stable rather than glyph-based.
