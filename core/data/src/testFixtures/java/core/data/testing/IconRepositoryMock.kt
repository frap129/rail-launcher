package core.data.testing

import core.data.icons.IIconRepository
import core.data.icons.model.Icon
import core.data.icons.model.IconPack
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow

/** A MockK [IIconRepository] in the system icon pack, answering [inputIcon] for every request. */
fun createIconRepositoryMock(inputIcon: Icon): IIconRepository {
    val mockIconRepository: IIconRepository = mockk()
    every { mockIconRepository.iconPacks } returns MutableStateFlow(setOf(IconPack.SystemIconPack))
    coEvery { mockIconRepository.getIcon(any(), any(), any()) } returns inputIcon
    return mockIconRepository
}
