package core.data.testing

import core.data.folders.Folder
import core.data.folders.IFolderRepository
import core.data.widgets.AppWidgetId
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow

/** A MockK [IFolderRepository] over [folders], creating a Folder with the next id and deleting by key. */
fun createFolderRepositoryMock(folders: MutableStateFlow<List<Folder>> = MutableStateFlow(emptyList())): IFolderRepository {
    val mockFolderRepository: IFolderRepository = mockk()
    every { mockFolderRepository.folders } returns folders
    coEvery { mockFolderRepository.create(any(), any(), any()) } coAnswers {
        val name: String? = firstArg()
        val folder: Folder = Folder(
            folderId = (folders.value.maxOfOrNull { existing -> existing.folderId } ?: 0L) + 1L,
            name = name?.takeUnless { it.isBlank() } ?: Folder.DEFAULT_NAME
        )
        folders.value = folders.value + folder
        folder
    }
    coEvery { mockFolderRepository.delete(any()) } coAnswers {
        val folder: Folder = firstArg()
        folders.value = folders.value.filterNot { existing -> existing.key == folder.key }
        emptyList<AppWidgetId>()
    }
    return mockFolderRepository
}
