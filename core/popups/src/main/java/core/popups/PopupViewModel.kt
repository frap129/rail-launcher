package core.popups

import android.os.UserHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.apps.model.App
import core.data.launchables.ILaunchableRepository
import core.data.launchables.model.Launchable
import core.data.shortcuts.IShortcutSource
import core.data.shortcuts.ShortcutsResult
import core.ui.layout.HomeEditMode
import core.ui.layout.HomeEditState
import core.util.WHILE_SUBSCRIBED_TIMEOUT_MS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

private data class OpenRequest(val ownerKey: String, val packageName: String, val profile: UserHandle, val anchor: PopupAnchor)

/** Owns the one open Pop-up, its anchor and its live shortcut content. */
class PopupViewModel(
    private val launchableRepository: ILaunchableRepository,
    private val shortcutSource: IShortcutSource,
    private val homeEditState: HomeEditState
) : ViewModel() {
    private val openRequest: MutableStateFlow<OpenRequest?> = MutableStateFlow(null)
    private val shortcuts: MutableStateFlow<ShortcutsResult> = MutableStateFlow(ShortcutsResult.Available(shortcuts = emptyList()))
    private val reloads: MutableStateFlow<Int> = MutableStateFlow(0)

    val popupState: StateFlow<PopupUiState> = combine(
        openRequest,
        launchableRepository.launchableState,
        shortcuts
    ) { request, launchableState, shortcutResult ->
        val owner: Launchable? = request?.let { open -> launchableState.launchables.firstOrNull { it.key == open.ownerKey } }
        if (request == null || owner == null) {
            PopupUiState.Closed
        } else {
            PopupUiState.Open(
                owner = owner,
                anchor = request.anchor,
                content = PopupContentResolver.resolve(owner = owner, shortcuts = shortcutResult)
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = PopupUiState.Closed
    )

    init {
        viewModelScope.launch {
            try {
                combine(openRequest, reloads) { request, reload -> request to reload }
                    .flatMapLatest { (request, _) ->
                        if (request == null) {
                            flowOf(ShortcutsResult.Available(shortcuts = emptyList()))
                        } else {
                            shortcutSource.shortcuts(packageName = request.packageName, profile = request.profile)
                        }
                    }
                    .collect { result -> shortcuts.value = result }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Timber.e(exception, "Popup shortcut collection failed")
            }
        }
        viewModelScope.launch {
            combine(
                openRequest,
                launchableRepository.launchableState,
                homeEditState.mode
            ) { request, launchableState, mode -> Triple(request, launchableState, mode) }
                .collect { (request, launchableState, mode) ->
                    if (request == null) {
                        return@collect
                    }
                    val isOwnerMissing: Boolean =
                        launchableState.isLoaded && launchableState.launchables.none { launchable -> launchable.key == request.ownerKey }
                    if (isOwnerMissing || mode != HomeEditMode.None) {
                        close()
                    }
                }
        }
    }

    fun open(owner: Launchable, anchor: PopupAnchor) {
        val app: App = owner as? App ?: return
        if (homeEditState.mode.value != HomeEditMode.None) {
            return
        }
        openRequest.value = OpenRequest(
            ownerKey = app.key,
            packageName = app.packageName,
            profile = app.profile,
            anchor = anchor
        )
    }

    fun close() {
        openRequest.value = null
    }

    /** Moves an open Pop-up to its row's latest position after a relayout. */
    fun onAnchorChanged(ownerKey: String, anchor: PopupAnchor) {
        openRequest.update { request ->
            if (request?.ownerKey == ownerKey) request.copy(anchor = anchor) else request
        }
    }

    fun onBack(): Boolean {
        if (openRequest.value == null) {
            return false
        }
        close()
        return true
    }

    fun onHomeIntent() {
        close()
    }

    fun onLaunched() {
        close()
    }

    /** Queries the owner's shortcuts again, for example after a work profile is resumed. */
    fun reloadShortcuts() {
        reloads.update { reload -> reload + 1 }
    }
}
