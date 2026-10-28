package core.data.testing

import core.data.folders.Folder
import core.data.folders.IFolderRepository
import core.data.widgets.AppWidgetId
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow

/** The Folder a stubbed create answers with by default: the first id the database allocates. */
private val defaultCreatedFolder: Folder = Folder(folderId = 1L)

/** A MockK [IFolderRepository] over [folders], creating [createdFolder] and freeing no App Widgets. */
fun createFolderRepositoryMock(
    folders: MutableStateFlow<List<Folder>> = MutableStateFlow(emptyList()),
    createdFolder: Folder = defaultCreatedFolder
): IFolderRepository {
    val mockFolderRepository: IFolderRepository = mockk()
    every { mockFolderRepository.folders } returns folders
    coEvery { mockFolderRepository.create(any(), any(), any()) } returns createdFolder
    coEvery { mockFolderRepository.delete(any()) } returns emptyList<AppWidgetId>()
    return mockFolderRepository
}
