package core.data.testing

import core.data.icons.IIconRepository
import core.data.icons.model.Icon
import core.data.icons.model.IconPack
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A MockK [IIconRepository] answering [inputIcon] for every icon request. */
fun createIconRepositoryMock(
    inputIcon: Icon,
    customIcon: Icon? = null,
    iconPacks: StateFlow<Set<IconPack>> = MutableStateFlow(setOf(IconPack.SystemIconPack)),
    packIcons: List<Icon.PackDrawableIcon> = emptyList()
): IIconRepository {
    val mockIconRepository: IIconRepository = mockk()
    every { mockIconRepository.iconPacks } returns iconPacks
    coEvery { mockIconRepository.getIcon(any(), any(), any()) } returns inputIcon
    coEvery { mockIconRepository.getCustomIcon(any(), any()) } returns customIcon
    coEvery { mockIconRepository.getPackIcons(any()) } returns packIcons
    return mockIconRepository
}
