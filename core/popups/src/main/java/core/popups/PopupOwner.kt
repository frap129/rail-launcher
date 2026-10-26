package core.popups

import android.os.UserHandle
import core.data.apps.model.App
import core.data.folders.Folder
import core.data.launchables.model.Launchable
import core.data.popups.IPopupRepository
import core.data.popups.PopupItem
import core.data.shortcuts.IShortcutSource
import core.data.shortcuts.ShortcutsResult
import core.util.WHILE_SUBSCRIBED_TIMEOUT_MS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

val EMPTY_SHORTCUTS: ShortcutsResult = ShortcutsResult.Available(shortcuts = emptyList())

/** The owner key and, for an App, the package and profile it publishes Shortcuts from. */
data class ShortcutTarget(val ownerKey: String, val packageName: String?, val profile: UserHandle?)

data class HiddenShortcutIds(val ownerKey: String?, val ids: Set<String>)

data class OwnerItems(val ownerKey: String?, val items: List<PopupItem>)

fun targetFor(owner: Launchable): ShortcutTarget? = when (owner) {
    is App -> ShortcutTarget(ownerKey = owner.key, packageName = owner.packageName, profile = owner.profile)
    is Folder -> ShortcutTarget(ownerKey = owner.key, packageName = null, profile = null)
    else -> null
}

/** The Launchable with [key], or null when it is not available. */
fun List<Launchable>.findLaunchable(key: String): Launchable? = firstOrNull { launchable -> launchable.key == key }

fun hiddenShortcutIds(
    scope: CoroutineScope,
    targets: Flow<ShortcutTarget?>,
    popupRepository: IPopupRepository
): StateFlow<HiddenShortcutIds> = ownerScoped(
    scope = scope,
    targets = targets.map { target -> target?.ownerKey },
    emptyValue = { ownerKey -> HiddenShortcutIds(ownerKey = ownerKey, ids = emptySet()) },
    source = { ownerKey ->
        popupRepository.hiddenShortcuts(ownerKey).map { ids -> HiddenShortcutIds(ownerKey = ownerKey, ids = ids) }
    }
)

fun <T> ownerScoped(scope: CoroutineScope, targets: Flow<String?>, emptyValue: (String?) -> T, source: (String) -> Flow<T>): StateFlow<T> =
    targets
        .distinctUntilChanged()
        .flatMapLatest { ownerKey ->
            if (ownerKey == null) {
                flowOf(emptyValue(null))
            } else {
                source(ownerKey).onStart { emit(emptyValue(ownerKey)) }
            }
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
            initialValue = emptyValue(null)
        )

fun addedItems(scope: CoroutineScope, targets: Flow<ShortcutTarget?>, popupRepository: IPopupRepository): StateFlow<OwnerItems> =
    ownerScoped(
        scope = scope,
        targets = targets.map { target -> target?.ownerKey },
        emptyValue = { ownerKey -> OwnerItems(ownerKey = ownerKey, items = emptyList()) },
        source = { ownerKey ->
            popupRepository.items(ownerKey).map { items -> OwnerItems(ownerKey = ownerKey, items = items) }
        }
    )

/** Queries [targets]'s Shortcuts again whenever [reloads] emits and stores the result in [into]. */
fun collectShortcuts(
    scope: CoroutineScope,
    targets: Flow<ShortcutTarget?>,
    reloads: Flow<Int>,
    shortcutSource: IShortcutSource,
    into: MutableStateFlow<ShortcutsResult?>
) {
    scope.launch {
        combine(targets, reloads) { target, _ -> target }
            .flatMapLatest { target ->
                if (target == null) {
                    flowOf(EMPTY_SHORTCUTS)
                } else {
                    shortcutsFor(target = target, shortcutSource = shortcutSource)
                }
            }
            .collect { result ->
                try {
                    into.value = result
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Timber.e(exception, "Popup shortcut collection failed")
                }
            }
    }
}

private fun shortcutsFor(target: ShortcutTarget, shortcutSource: IShortcutSource): Flow<ShortcutsResult> {
    val packageName: String = target.packageName ?: return flowOf(EMPTY_SHORTCUTS)
    val profile: UserHandle = target.profile ?: return flowOf(EMPTY_SHORTCUTS)
    return shortcutSource.shortcuts(packageName = packageName, profile = profile)
        .catch { exception ->
            if (exception is CancellationException) {
                throw exception
            }
            Timber.e(exception, "Popup shortcut collection failed")
            emit(EMPTY_SHORTCUTS)
        }
}
