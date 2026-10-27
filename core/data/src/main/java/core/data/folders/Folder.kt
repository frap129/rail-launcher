package core.data.folders

import android.content.Context
import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableAppearance
import core.data.launchables.model.LaunchableNames
import core.data.prefs.LaunchableCustomization
import core.data.prefs.toLaunchableNames
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import timber.log.Timber

private const val FOLDER_KEY_PREFIX: String = "rail-folder:"
private const val FOLDER_SHADE_COUNT: Long = 3L

/** A Launchable that only opens a Pop-up listing the Launchables the user put in it. */
data class Folder(val folderId: Long, override val name: String = DEFAULT_NAME) : Launchable() {
    override val key: String = "$FOLDER_KEY_PREFIX$folderId"
    override val defaultName: String = DEFAULT_NAME
    override val defaultIcon: Icon = Icon.FolderIcon(shade = folderShade(folderId))
    override val icon: Icon get() = defaultIcon

    override fun withCustomization(appearance: LaunchableAppearance): Folder = copy(name = appearance.name)

    override fun launch(context: Context) {
        Timber.w("Folder $key cannot be launched; open its Pop-up instead")
    }

    companion object {
        const val DEFAULT_NAME: String = "Folder"
    }
}

private fun folderShade(folderId: Long): Int = (folderId % FOLDER_SHADE_COUNT).toInt()

/** Resolves the stored Folder records with their name customizations. */
internal fun Flow<List<FolderRecord>>.resolveFolders(customizations: Flow<List<LaunchableCustomization>>): Flow<List<Folder>> =
    combine(this, customizations) { records, customizations ->
        records.resolveFolders(names = customizations.toLaunchableNames())
    }

/** Resolves stored Folder records with the names customized for their keys. */
internal fun List<FolderRecord>.resolveFolders(names: LaunchableNames): List<Folder> = map { record -> record.toFolder(names = names) }

/** Resolves a Folder record's stored name, falling back to the default. */
private fun FolderRecord.toFolder(names: LaunchableNames): Folder {
    val folder: Folder = Folder(folderId = id)
    return folder.copy(name = folder.resolveName(names))
}
