//[popups](../../index.md)/[core.popups](index.md)/[FolderNameDialog](-folder-name-dialog.md)

# FolderNameDialog

[main]\
fun [FolderNameDialog](-folder-name-dialog.md)(initialName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) = Folder.DEFAULT_NAME, title: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) = stringResource(R.string.new_folder), confirmLabel: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) = stringResource(R.string.create), onConfirm: (name: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)) -&gt; [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html), onDismiss: () -&gt; [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html))

Asks for a Folder's name, prefilled, selected and trimmed to the Launchable name limit.
