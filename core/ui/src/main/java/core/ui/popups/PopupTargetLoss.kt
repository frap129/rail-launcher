package core.ui.popups

import core.data.launchables.ILaunchableRepository
import core.data.launchables.isKeptKey
import core.ui.layout.HomeEditMode
import core.ui.layout.HomeEditState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber

/** Closes the layer that owns [targetKeys] when its Launchable disappears or a Home edit mode takes over. */
fun observeTargetLoss(
    scope: CoroutineScope,
    targetKeys: Flow<String?>,
    launchableRepository: ILaunchableRepository,
    homeEditState: HomeEditState,
    isClosedByMode: (HomeEditMode) -> Boolean,
    onTargetLost: () -> Unit
) {
    scope.launch {
        combine(
            targetKeys,
            launchableRepository.launchableState,
            homeEditState.mode
        ) { targetKey, launchableState, mode -> Triple(targetKey, launchableState, mode) }
            .collect { (targetKey, launchableState, mode) ->
                try {
                    if (targetKey == null) {
                        return@collect
                    }
                    val isOwnerMissing: Boolean =
                        launchableState.isLoaded &&
                            launchableState.launchables.none { launchable -> launchable.key == targetKey }
                    if (isOwnerMissing || isClosedByMode(mode)) {
                        onTargetLost()
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Timber.e(exception, "Popup target loss observation failed")
                }
            }
    }
}

/**
 * Dismisses the layer showing [contentKeys] (its Owner and the Launchables it lists) when a
 * Profile becomes Locked after the layer showed its content.
 */
fun observeLockedContent(
    scope: CoroutineScope,
    contentKeys: Flow<Set<String>>,
    launchableRepository: ILaunchableRepository,
    onContentLocked: () -> Unit
) {
    scope.launch {
        var previousKeys: Set<String> = emptySet()
        combine(contentKeys, launchableRepository.launchableState) { keys, state -> keys to state }
            .collect { (keys, state) ->
                try {
                    val isContentLocked: Boolean = state.isLoaded && previousKeys.any { key -> state.isKeptKey(key) }
                    if (isContentLocked) {
                        onContentLocked()
                    }
                    previousKeys = if (isContentLocked) emptySet() else keys
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Timber.e(exception, "Popup Locked content observation failed")
                }
            }
    }
}
