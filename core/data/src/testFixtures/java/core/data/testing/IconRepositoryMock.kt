package core.data.testing

import core.data.icons.IIconRepository
import core.data.icons.model.Icon
import core.data.icons.model.IconPack
import core.data.icons.model.IconPackCategory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/** A MockK [IIconRepository] answering [inputIcon] for every icon request. */
fun createIconRepositoryMock(
    inputIcon: Icon,
    customIcon: Icon? = null,
    iconPacks: StateFlow<Set<IconPack>> = MutableStateFlow(setOf(IconPack.SystemIconPack)),
    packCategories: List<IconPackCategory> = emptyList(),
    suggestedIcons: Flow<List<Icon>> = flowOf(emptyList())
): IIconRepository {
    val mockIconRepository: IIconRepository = mockk()
    every { mockIconRepository.iconPacks } returns iconPacks
    coEvery { mockIconRepository.getIcon(any(), any(), any()) } returns inputIcon
    coEvery { mockIconRepository.getCustomIcon(any(), any()) } returns customIcon
    coEvery { mockIconRepository.getPackCategories(any()) } returns packCategories
    every { mockIconRepository.getSuggestedIcons(any()) } returns suggestedIcons
    return mockIconRepository
}
