package core.data.folders

import android.content.Context
import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableAppearance
import timber.log.Timber

private const val FOLDER_KEY_PREFIX: String = "rail-folder:"
private const val FOLDER_SHADE_COUNT: Long = 3L

/** A Launchable that only opens a Pop-up listing the Launchables the user put in it. */
data class Folder(
    val folderId: Long,
    val showInAppList: Boolean = false,
    override val name: String = DEFAULT_NAME,
    override val icon: Icon = Icon.FolderIcon(shade = folderShade(folderId))
) : Launchable() {
    override val key: String = "$FOLDER_KEY_PREFIX$folderId"
    override val defaultName: String = DEFAULT_NAME
    override val defaultIcon: Icon = Icon.FolderIcon(shade = shade)

    /** The derived accent shade; never stored. */
    val shade: Int get() = folderShade(folderId)

    /** Whether the user chose to show this Folder in the App List. */
    val isInAppList: Boolean get() = showInAppList

    override fun withCustomization(appearance: LaunchableAppearance): Folder = copy(name = appearance.name)

    override fun launch(context: Context) {
        Timber.w("Folder $key cannot be launched; open its Pop-up instead")
    }

    companion object {
        const val DEFAULT_NAME: String = "Folder"
    }
}

private fun folderShade(folderId: Long): Int = (folderId % FOLDER_SHADE_COUNT).toInt()

/** Resolves a Folder record's stored name, falling back to the default. */
internal fun FolderRecord.toFolder(names: Map<String, String?>): Folder {
    val folder: Folder = Folder(folderId = id, showInAppList = showInAppList)
    return folder.copy(name = names[folder.key] ?: folder.defaultName)
}
