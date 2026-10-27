package core.data.folders

import core.data.favorites.IFavoritesRepository
import core.data.popups.IPopupRepository
import core.data.prefs.IPreferencesRepository
import core.data.room.TransactionEffects
import core.data.room.dao.FolderDao
import core.data.room.dao.LaunchableCustomizationDao
import core.data.runCleanup
import core.data.widgets.AppWidgetId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * A Folder spans the Folder table, its added items and hidden Shortcuts, its Pop-up Place, its name
 * customization and the Favorites, so a failure must not leave a partial Folder.
 *
 * [delete] runs the Folder row, its Pop-up rows, its Pop-up Place and its name customization in one
 * Room transaction. The Favorites row cannot join it: the Favorites repository waits for its resolved
 * Flow to see a write, and that Flow cannot update until the transaction commits, so the wait can
 * run to its timeout. It is removed after commit, with the Favorites repository's stale purge as the
 * fallback. Releasing system App Widget ids and Shortcut pins also waits for the commit, so a
 * rollback cannot leave rows pointing at ids that are already gone.
 *
 * [create] writes in order and undoes what it wrote when a later write fails. A transaction would
 * make the Folder visible together with its rows, so the Pop-up and Favorites purges would get no
 * head start before the launchable resolution reacts. Ordering narrows that race without removing
 * it; the purges remain the fallback.
 *
 * The name customization is written through its DAO: on delete it joins the transaction, and on
 * create it is the last write, removed if an earlier write fails.
 */
class IFolderRepositoryImpl(
    private val folderDao: FolderDao,
    private val favoritesRepository: IFavoritesRepository,
    private val popupRepository: IPopupRepository,
    private val preferencesRepository: IPreferencesRepository,
    private val launchableCustomizationDao: LaunchableCustomizationDao
) : IFolderRepository {
    override val folders: Flow<List<Folder>> = folderDao.observeAll()
        .resolveFolders(customizations = preferencesRepository.launchableCustomizations)

    override suspend fun create(name: String?, initialItems: List<String>, addToFavorites: Boolean): Folder {
        val resolvedName: String? = name?.takeUnless { value -> value.isBlank() || value == Folder.DEFAULT_NAME }
        val folderId: Long = folderDao.insert(record = FolderRecord())
        val folder: Folder = Folder(folderId = folderId, name = resolvedName ?: Folder.DEFAULT_NAME)
        try {
            if (initialItems.isNotEmpty()) {
                popupRepository.setItems(ownerKey = folder.key, add = initialItems, remove = emptySet())
            }
            if (addToFavorites) {
                favoritesRepository.add(folder)
            }
            if (resolvedName != null) {
                launchableCustomizationDao.setName(key = folder.key, name = resolvedName)
            }
        } catch (exception: CancellationException) {
            withContext(NonCancellable) { discardFolder(folder) }
            throw exception
        } catch (exception: Exception) {
            discardFolder(folder)
            throw exception
        }
        return folder
    }

    override suspend fun delete(folder: Folder): List<AppWidgetId> {
        val effects: TransactionEffects = TransactionEffects()
        val freedIds: List<AppWidgetId> = folderDao.inTransaction {
            withContext(effects) {
                val deletedIds: List<AppWidgetId> = deleteFolderRows(folder = folder, tolerateFailures = false)
                launchableCustomizationDao.delete(key = folder.key)
                deletedIds
            }
        }
        effects.run()
        removeFavorite(folder)
        return freedIds
    }

    /** Undoes a failed [create]; a failure in one step does not skip the remaining steps. */
    private suspend fun discardFolder(folder: Folder) {
        deleteFolderRows(folder = folder, tolerateFailures = true)
        runCleanup(message = "Failed to delete the name of a failed create") { launchableCustomizationDao.delete(key = folder.key) }
        removeFavorite(folder)
        // The Folder row is what the caller sees, so try its delete once more after the purgeable rows.
        deleteStep(tolerateFailures = true, message = "Failed to delete the Folder of a failed create") {
            folderDao.delete(id = folder.folderId)
        }
    }

    /** Deletes the Folder's Pop-up rows and then the Folder row, keeping the Widget release deferred. */
    private suspend fun deleteFolderRows(folder: Folder, tolerateFailures: Boolean): List<AppWidgetId> {
        deleteStep(tolerateFailures, message = "Failed to delete added items that referenced ${folder.key}") {
            popupRepository.deleteItemsByKey(itemKeys = listOf(folder.key))
        }
        var freedIds: List<AppWidgetId> = emptyList()
        deleteStep(tolerateFailures, message = "Failed to delete the Pop-up of ${folder.key}") {
            freedIds = popupRepository.deleteOwner(ownerKey = folder.key)
        }
        deleteStep(tolerateFailures, message = "Failed to delete ${folder.key}") {
            folderDao.delete(id = folder.folderId)
        }
        return freedIds
    }

    private suspend fun deleteStep(tolerateFailures: Boolean, message: String, action: suspend () -> Unit) {
        if (tolerateFailures) {
            runCleanup(message = message, action = action)
        } else {
            action()
        }
    }

    /** Removes the Folder's Favorite after commit; the stale Favorites purge is the fallback. */
    private suspend fun removeFavorite(folder: Folder) {
        runCleanup(message = "Failed to remove ${folder.key} from the Favorites") { favoritesRepository.remove(folder) }
    }
}
