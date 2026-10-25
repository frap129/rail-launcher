package core.data.folders

import core.data.favorites.IFavoritesRepository
import core.data.launchables.model.Launchable
import core.data.testing.FakeFolderDao
import core.data.testing.FakePopupRepository
import core.data.testing.FakePreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ITEM_KEY: String = "com.example.app_0"

class IFolderRepositoryImplTest {
    @Test
    fun `create derives the shade from the id and stores the name and items`() = runTest {
        val mockFolderDao: FakeFolderDao = FakeFolderDao()
        val mockPreferencesRepository: FakePreferencesRepository = FakePreferencesRepository()
        val mockFavoritesRepository: RecordingFavoritesRepository = RecordingFavoritesRepository()
        val mockPopupRepository: FakePopupRepository = FakePopupRepository()
        val repository: IFolderRepository = createRepository(
            mockFolderDao = mockFolderDao,
            mockPreferencesRepository = mockPreferencesRepository,
            mockFavoritesRepository = mockFavoritesRepository,
            mockPopupRepository = mockPopupRepository
        )

        val actualFolder: Folder = repository.create(name = "Games", initialItems = listOf(ITEM_KEY))

        assertEquals(1L, actualFolder.folderId)
        assertEquals(1, actualFolder.shade)
        assertEquals("Games", actualFolder.name)
        assertEquals(1, mockPreferencesRepository.renames.size)
        assertEquals("Games", mockPreferencesRepository.renames.single().name)
        assertEquals(listOf(actualFolder.key), mockFavoritesRepository.added.map { folder -> folder.key })
        assertEquals(
            listOf(Triple(actualFolder.key, listOf(ITEM_KEY), emptySet<String>())),
            mockPopupRepository.setItemsCalls
        )
    }

    @Test
    fun `create keeps the default name when the name is blank or the default`() = runTest {
        val mockPreferencesRepository: FakePreferencesRepository = FakePreferencesRepository()
        val repository: IFolderRepository = createRepository(
            mockPreferencesRepository = mockPreferencesRepository,
            mockFavoritesRepository = RecordingFavoritesRepository()
        )

        val blankNamedFolder: Folder = repository.create(name = "   ")
        val defaultNamedFolder: Folder = repository.create(name = Folder.DEFAULT_NAME)

        assertEquals(Folder.DEFAULT_NAME, blankNamedFolder.name)
        assertEquals(Folder.DEFAULT_NAME, defaultNamedFolder.name)
        assertTrue(mockPreferencesRepository.renames.isEmpty())
    }

    @Test
    fun `create can leave the Folder out of Favorites`() = runTest {
        val mockFavoritesRepository: RecordingFavoritesRepository = RecordingFavoritesRepository()
        val repository: IFolderRepository = createRepository(
            mockFavoritesRepository = mockFavoritesRepository
        )

        repository.create(name = "Games", addToFavorites = false)

        assertTrue(mockFavoritesRepository.added.isEmpty())
    }

    @Test
    fun `delete cascades through its Pop-up, Favorites and customization`() = runTest {
        val inputFolder: Folder = Folder(folderId = 3L)
        val mockFolderDao: FakeFolderDao = FakeFolderDao(initialRecords = listOf(FolderRecord(id = inputFolder.folderId)))
        val mockPreferencesRepository: FakePreferencesRepository = FakePreferencesRepository()
        val mockFavoritesRepository: RecordingFavoritesRepository = RecordingFavoritesRepository()
        val mockPopupRepository: FakePopupRepository = FakePopupRepository()
        val repository: IFolderRepository = createRepository(
            mockFolderDao = mockFolderDao,
            mockPreferencesRepository = mockPreferencesRepository,
            mockFavoritesRepository = mockFavoritesRepository,
            mockPopupRepository = mockPopupRepository
        )

        repository.delete(inputFolder)

        assertTrue(mockFolderDao.observeAll().firstValue().isEmpty())
        assertEquals(listOf(inputFolder.key), mockPopupRepository.deletedOwners)
        assertEquals(listOf(listOf(inputFolder.key)), mockPopupRepository.deletedItemKeys)
        assertEquals(listOf(inputFolder.key), mockFavoritesRepository.removed.map { folder -> folder.key })
        assertEquals(listOf(inputFolder.key), mockPreferencesRepository.resets.map { folder -> folder.key })
        assertEquals(0, inputFolder.shade)
    }

    @Test
    fun `setShowInAppList stores the flag`() = runTest {
        val inputFolder: Folder = Folder(folderId = 1L)
        val mockFolderDao: FakeFolderDao = FakeFolderDao(initialRecords = listOf(FolderRecord(id = inputFolder.folderId)))
        val repository: IFolderRepository = createRepository(mockFolderDao = mockFolderDao)

        repository.setShowInAppList(folder = inputFolder, show = true)

        assertTrue(mockFolderDao.observeAll().firstValue().single().showInAppList)
    }

    private fun createRepository(
        mockFolderDao: FakeFolderDao = FakeFolderDao(),
        mockPreferencesRepository: FakePreferencesRepository = FakePreferencesRepository(),
        mockFavoritesRepository: IFavoritesRepository = RecordingFavoritesRepository(),
        mockPopupRepository: FakePopupRepository = FakePopupRepository()
    ): IFolderRepository = IFolderRepositoryImpl(
        folderDao = mockFolderDao,
        favoritesRepository = mockFavoritesRepository,
        popupRepository = mockPopupRepository,
        preferencesRepository = mockPreferencesRepository
    )

    private suspend fun Flow<List<FolderRecord>>.firstValue(): List<FolderRecord> = first()
}

private class RecordingFavoritesRepository(initialFavorites: List<Launchable> = emptyList()) : IFavoritesRepository {
    private val storedFavorites: MutableStateFlow<List<Launchable>> = MutableStateFlow(initialFavorites)
    val added: MutableList<Launchable> = mutableListOf()
    val removed: MutableList<Launchable> = mutableListOf()

    override val favorites: Flow<List<Launchable>> = storedFavorites

    override fun isFavorite(launchable: Launchable): Flow<Boolean> = storedFavorites.map { favorites ->
        favorites.any { item -> item.key == launchable.key }
    }

    override suspend fun add(launchable: Launchable) {
        added += launchable
        storedFavorites.update { favorites -> if (favorites.any { it.key == launchable.key }) favorites else favorites + launchable }
    }

    override suspend fun remove(launchable: Launchable) {
        removed += launchable
        storedFavorites.update { favorites -> favorites.filterNot { item -> item.key == launchable.key } }
    }

    override suspend fun reorder(favorites: List<Launchable>) {
        storedFavorites.value = favorites
    }
}
