package core.data.folders

import core.data.favorites.IFavoritesRepository
import core.data.popups.IPopupRepository
import core.data.prefs.IPreferencesRepository
import core.data.room.dao.FolderDao
import core.data.widgets.AppWidgetId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class IFolderRepositoryImpl(
    private val folderDao: FolderDao,
    private val favoritesRepository: IFavoritesRepository,
    private val popupRepository: IPopupRepository,
    private val preferencesRepository: IPreferencesRepository
) : IFolderRepository {
    override val folders: Flow<List<Folder>> = combine(
        folderDao.observeAll(),
        preferencesRepository.launchableCustomizations
    ) { records, customizations ->
        val names: Map<String, String?> = customizations.associate { customization -> customization.key to customization.name }
        records.map { record -> record.toFolder(names = names) }
    }

    override suspend fun create(name: String?, initialItems: List<String>, addToFavorites: Boolean): Folder {
        val folderId: Long = folderDao.insert(record = FolderRecord())
        val resolvedName: String? = name?.takeUnless { value -> value.isBlank() || value == Folder.DEFAULT_NAME }
        val folder: Folder = Folder(folderId = folderId, name = resolvedName ?: Folder.DEFAULT_NAME)
        if (resolvedName != null) {
            preferencesRepository.setItemName(item = folder, name = resolvedName)
        }
        if (initialItems.isNotEmpty()) {
            popupRepository.setItems(ownerKey = folder.key, add = initialItems, remove = emptySet())
        }
        if (addToFavorites) {
            favoritesRepository.add(folder)
        }
        return folder
    }

    override suspend fun delete(folder: Folder): List<AppWidgetId> {
        folderDao.delete(id = folder.folderId)
        val freedIds: List<AppWidgetId> = popupRepository.deleteOwner(ownerKey = folder.key)
        popupRepository.deleteItemsByKey(itemKeys = listOf(folder.key))
        favoritesRepository.remove(folder)
        preferencesRepository.resetItemName(item = folder)
        return freedIds
    }

    override suspend fun setShowInAppList(folder: Folder, show: Boolean) {
        folderDao.setShowInAppList(id = folder.folderId, showInAppList = show)
    }
}
