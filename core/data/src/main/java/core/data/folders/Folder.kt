package core.data.folders

import android.content.Context
import core.data.icons.IIconRepository
import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableAppearance
import core.data.launchables.model.LaunchableIcons
import core.data.launchables.model.LaunchableNames
import core.data.launchables.model.resolveCustomIcon
import timber.log.Timber

private const val FOLDER_KEY_PREFIX: String = "rail-folder:"
private const val FOLDER_SHADE_COUNT: Long = 3L

/** A Launchable that only opens a Pop-up listing the Launchables the user put in it. */
data class Folder(
    val folderId: Long,
    override val name: String = DEFAULT_NAME,
    override val defaultIcon: Icon = Icon.FolderIcon(shade = folderShade(folderId)),
    override val icon: Icon = defaultIcon
) : Launchable() {
    override val key: String = "$FOLDER_KEY_PREFIX$folderId"
    override val defaultName: String = DEFAULT_NAME
    override val supportsCustomIcon: Boolean = true

    override fun withCustomization(appearance: LaunchableAppearance): Folder = copy(name = appearance.name, icon = appearance.icon)

    override fun launch(context: Context) {
        Timber.w("Folder $key cannot be launched; open its Pop-up instead")
    }

    companion object {
        const val DEFAULT_NAME: String = "Folder"
    }
}

private fun folderShade(folderId: Long): Int = (folderId % FOLDER_SHADE_COUNT).toInt()

/** Resolves stored Folder records with the names and Custom icons customized for their keys. */
internal suspend fun List<FolderRecord>.resolveFolders(
    names: LaunchableNames,
    icons: LaunchableIcons,
    iconRepository: IIconRepository
): List<Folder> = map { record -> record.toFolder(names = names, icons = icons, iconRepository = iconRepository) }

/** Resolves a Folder record's Custom icon, falling back to its tile, and its stored name. */
private suspend fun FolderRecord.toFolder(names: LaunchableNames, icons: LaunchableIcons, iconRepository: IIconRepository): Folder {
    val folder: Folder = Folder(folderId = id)
    val customIcon: Icon? = icons.resolveCustomIcon(key = folder.key, iconRepository = iconRepository)
    return folder.withCustomization(
        LaunchableAppearance(name = folder.resolveName(names), icon = customIcon ?: folder.defaultIcon)
    )
}
