package core.data.testing

import core.data.folders.Folder
import core.data.folders.IFolderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

data class FolderCreateRequest(val name: String?, val initialItems: List<String>, val addToFavorites: Boolean)

class FakeFolderRepository(initialFolders: List<Folder> = emptyList()) : IFolderRepository {
    private val storedFolders: MutableStateFlow<List<Folder>> = MutableStateFlow(initialFolders)
    private var nextFolderId: Long = (initialFolders.maxOfOrNull { folder -> folder.folderId } ?: 0L) + 1L
    val createRequests: MutableList<FolderCreateRequest> = mutableListOf()
    val deletedFolders: MutableList<Folder> = mutableListOf()
    val showInAppListChanges: MutableList<Pair<Folder, Boolean>> = mutableListOf()

    override val folders: Flow<List<Folder>> = storedFolders

    override suspend fun create(name: String?, initialItems: List<String>, addToFavorites: Boolean): Folder {
        val folder: Folder = Folder(
            folderId = nextFolderId++,
            name = name?.takeUnless { it.isBlank() } ?: Folder.DEFAULT_NAME
        )
        createRequests += FolderCreateRequest(name = name, initialItems = initialItems, addToFavorites = addToFavorites)
        storedFolders.update { current -> current + folder }
        return folder
    }

    override suspend fun delete(folder: Folder) {
        deletedFolders += folder
        storedFolders.update { current -> current.filterNot { existing -> existing.key == folder.key } }
    }

    override suspend fun setShowInAppList(folder: Folder, show: Boolean) {
        showInAppListChanges += folder to show
        storedFolders.update { current ->
            current.map { existing -> if (existing.key == folder.key) existing.copy(showInAppList = show) else existing }
        }
    }
}
