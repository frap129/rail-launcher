package core.data.prefs

import core.data.icons.IIconImageStore
import core.data.icons.model.CustomIcon
import core.data.icons.model.IconPack
import core.data.launchables.model.Launchable
import core.data.room.DatabaseTransaction
import core.data.room.TransactionEffects
import core.data.room.dao.LaunchableCustomizationDao
import core.data.room.dao.SettingsDao
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class IPreferencesRepositoryImpl(
    private val launchableCustomizationDao: LaunchableCustomizationDao,
    private val settingsDao: SettingsDao,
    private val imageStore: IIconImageStore,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : IPreferencesRepository {
    private val iconWriteMutex: Mutex = Mutex()
    override val launchableCustomizations: Flow<List<LaunchableCustomization>> = launchableCustomizationDao.observeAll()
    override val settings: Flow<Settings> = settingsDao.observe()

    override fun setItemName(item: Launchable, name: String) {
        if (name.isBlank()) {
            resetItemName(item)
            return
        }
        scope.launch(dispatcher) {
            launchableCustomizationDao.setName(key = item.key, name = name)
        }
    }

    override fun resetItemName(item: Launchable) {
        scope.launch(dispatcher) {
            launchableCustomizationDao.resetName(key = item.key)
        }
    }

    override fun setCustomIcon(item: Launchable, icon: CustomIcon) {
        scope.launch(dispatcher) {
            withIconWriteLock {
                val previousIcon: CustomIcon? = launchableCustomizationDao.get(key = item.key)?.icon
                launchableCustomizationDao.setIcon(key = item.key, icon = icon)
                deleteReplacedImage(previousIcon = previousIcon, icon = icon)
            }
        }
    }

    override fun resetCustomIcon(item: Launchable) {
        scope.launch(dispatcher) {
            withIconWriteLock {
                val previousIcon: CustomIcon? = launchableCustomizationDao.get(key = item.key)?.icon
                launchableCustomizationDao.resetIcon(key = item.key)
                deleteReplacedImage(previousIcon = previousIcon, icon = null)
            }
        }
    }

    override fun resetCustomIcons() {
        scope.launch(dispatcher) {
            withIconWriteLock {
                launchableCustomizationDao.resetIcons()
                imageStore.deleteAll()
            }
        }
    }

    override suspend fun removeLaunchableCustomizations(keys: List<String>) {
        if (keys.isEmpty()) {
            return
        }
        withIconWriteLock {
            val imageFiles: List<String> = launchableCustomizationDao.get(keys = keys).mapNotNull { customization ->
                (customization.icon as? CustomIcon.Image)?.fileName
            }
            launchableCustomizationDao.delete(keys = keys)
            deleteImageFilesAfterCommit(imageFiles = imageFiles)
        }
    }

    /** Deletes the file of [previousIcon] when it is an image the [icon] write replaced. */
    private fun deleteReplacedImage(previousIcon: CustomIcon?, icon: CustomIcon?) {
        val previousImage: CustomIcon.Image = previousIcon as? CustomIcon.Image ?: return
        if (previousImage == icon) {
            return
        }
        imageStore.delete(fileName = previousImage.fileName)
    }

    /** Queues [imageFiles] for deletion after the surrounding transaction commits, or deletes them now. */
    private suspend fun deleteImageFilesAfterCommit(imageFiles: List<String>) {
        val effects: TransactionEffects? = coroutineContext[TransactionEffects.Key]
        if (effects == null) {
            deleteImageFiles(imageFiles = imageFiles)
        } else {
            effects.afterCommit { deleteImageFiles(imageFiles = imageFiles) }
        }
    }

    private fun deleteImageFiles(imageFiles: List<String>) {
        imageFiles.forEach { fileName -> imageStore.delete(fileName = fileName) }
    }

    /**
     * Serializes icon writes outside a database transaction. Inside one the database already
     * serializes writes, and taking the lock there could invert locks against a writer waiting
     * for the write connection.
     */
    private suspend inline fun <T> withIconWriteLock(block: suspend () -> T): T = if (coroutineContext[DatabaseTransaction.Key] != null) {
        block()
    } else {
        iconWriteMutex.withLock { block() }
    }

    override fun setIconPack(iconPack: IconPack) {
        scope.launch(dispatcher) {
            val packageName: String? = (iconPack as? IconPack.CustomIconPack)?.packageName
            settingsDao.update { settings -> settings.copy(iconPack = packageName) }
        }
    }

    override fun setHidePrivateSpaceWhenLocked(hide: Boolean) {
        scope.launch(dispatcher) {
            settingsDao.update { settings -> settings.copy(hidePrivateSpaceWhenLocked = hide) }
        }
    }

    override fun setScrubEdges(edges: ScrubEdges) {
        scope.launch(dispatcher) {
            settingsDao.update { settings -> settings.copy(scrubEdges = edges) }
        }
    }

    override fun setRailSide(side: RailSide) {
        scope.launch(dispatcher) {
            settingsDao.update { settings -> settings.copy(railSide = side) }
        }
    }

    override fun setOpenSearchOnSwipeUp(enabled: Boolean) {
        scope.launch(dispatcher) {
            settingsDao.update { settings -> settings.copy(openSearchOnSwipeUp = enabled) }
        }
    }

    override fun setOpenSearchOnHomePress(enabled: Boolean) {
        scope.launch(dispatcher) {
            settingsDao.update { settings -> settings.copy(openSearchOnHomePress = enabled) }
        }
    }

    override fun setSearchSuggestions(enabled: Boolean) {
        scope.launch(dispatcher) {
            settingsDao.update { settings -> settings.copy(searchSuggestions = enabled) }
        }
    }

    override fun setSearchAutoLaunch(enabled: Boolean) {
        scope.launch(dispatcher) {
            settingsDao.update { settings -> settings.copy(searchAutoLaunch = enabled) }
        }
    }

    override fun setSearchBottomAnchoredResults(enabled: Boolean) {
        scope.launch(dispatcher) {
            settingsDao.update { settings -> settings.copy(searchBottomAnchoredResults = enabled) }
        }
    }

    override fun setSearchCalculatorRow(enabled: Boolean) {
        scope.launch(dispatcher) {
            settingsDao.update { settings -> settings.copy(searchCalculatorRow = enabled) }
        }
    }

    override fun setSearchWebRow(enabled: Boolean) {
        scope.launch(dispatcher) {
            settingsDao.update { settings -> settings.copy(searchWebRow = enabled) }
        }
    }

    override fun setSearchPlayStoreRow(enabled: Boolean) {
        scope.launch(dispatcher) {
            settingsDao.update { settings -> settings.copy(searchPlayStoreRow = enabled) }
        }
    }

    override fun setSearchWebSuggestions(enabled: Boolean) {
        scope.launch(dispatcher) {
            settingsDao.update { settings -> settings.copy(searchWebSuggestions = enabled) }
        }
    }

    override fun setSearchContacts(enabled: Boolean) {
        scope.launch(dispatcher) {
            settingsDao.update { settings -> settings.copy(searchContacts = enabled) }
        }
    }

    override fun setSearchEngine(engine: SearchEngine) {
        scope.launch(dispatcher) {
            settingsDao.update { settings -> settings.copy(searchEngine = engine) }
        }
    }

    override fun setCustomSearchEngineTemplate(template: String) {
        scope.launch(dispatcher) {
            settingsDao.update { settings -> settings.copy(searchEngine = SearchEngine.Custom, searchEngineTemplate = template) }
        }
    }
}
