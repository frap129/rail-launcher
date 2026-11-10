package core.data.testing

import core.data.icons.model.IconPack
import core.util.reemittingFlow
import kotlinx.coroutines.flow.MutableSharedFlow

/** The Icon pack set flow tests update, re-emitting an equal set as an Icon pack update does. */
fun createIconPackFlow(packs: Set<IconPack> = setOf(IconPack.SystemIconPack)): MutableSharedFlow<Set<IconPack>> =
    reemittingFlow(initialValue = packs)
