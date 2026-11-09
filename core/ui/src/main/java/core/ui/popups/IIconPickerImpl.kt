package core.ui.popups

import core.data.icons.IIconRepository
import core.data.icons.model.CustomIcon
import core.data.icons.model.Icon
import core.data.icons.model.IconPack
import core.data.icons.model.IconPackCategory
import core.data.icons.model.sortedCustomIconPacks
import core.data.launchables.ILaunchableRepository
import core.data.launchables.model.Launchable
import core.data.popups.findLaunchable
import core.data.prefs.IPreferencesRepository
import core.ui.layout.HomeEditMode
import core.ui.layout.HomeEditState
import core.util.WHILE_SUBSCRIBED_TIMEOUT_MS
import core.util.drawableNameContains
import core.util.launchLogged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private data class IconPickerRequest(val ownerKey: String, val packPackageName: String? = null, val query: String = "")

private data class PackContents(val packPackageName: String, val categories: List<IconPackCategory>)

/** Owns the Icon picker and the Custom icon writes it commits. */
class IIconPickerImpl(
    parentScope: CoroutineScope,
    private val launchableRepository: ILaunchableRepository,
    private val iconRepository: IIconRepository,
    private val preferencesRepository: IPreferencesRepository,
    private val homeEditState: HomeEditState,
    private val session: IPopupSession
) : IIconPicker {
    private val collaboratorScope: PopupCollaboratorScope = PopupCollaboratorScope(parentScope = parentScope)
    private val scope: CoroutineScope = collaboratorScope.scope

    private val request: MutableStateFlow<IconPickerRequest?> = MutableStateFlow(null)
    private val packContents: MutableStateFlow<PackContents?> = MutableStateFlow(null)
    private val suggestions: MutableStateFlow<List<Icon>> = MutableStateFlow(emptyList())
    private var suggestionsJob: Job? = null

    override val iconPickerState: StateFlow<IconPickerState> = combine(
        request,
        launchableRepository.launchableState,
        iconRepository.iconPacks,
        packContents,
        suggestions
    ) { currentRequest, launchableState, iconPacks, contents, currentSuggestions ->
        val owner: Launchable? = currentRequest?.ownerKey?.let { ownerKey ->
            launchableState.launchables.findLaunchable(key = ownerKey)
        }
        if (currentRequest == null || owner == null) {
            IconPickerState.Closed
        } else {
            val packs: List<IconPack.CustomIconPack> = iconPacks.sortedCustomIconPacks()
            val pack: IconPack.CustomIconPack? = currentRequest.packPackageName?.let { packageName ->
                packs.firstOrNull { candidate -> candidate.packageName == packageName }
            }
            if (pack == null) {
                IconPickerState.Listing(owner = owner, iconPacks = packs, suggestions = currentSuggestions)
            } else {
                val categories: List<IconPackCategory>? = contents
                    ?.takeIf { current -> current.packPackageName == pack.packageName }
                    ?.categories
                IconPickerState.Pack(
                    iconPack = pack,
                    categories = categories,
                    query = currentRequest.query,
                    results = categories?.let { current -> filterPackIcons(categories = current, query = currentRequest.query) }.orEmpty()
                )
            }
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = IconPickerState.Closed
    )

    init {
        observeTargetLoss(
            scope = scope,
            targetKeys = request.map { currentRequest -> currentRequest?.ownerKey },
            launchableRepository = launchableRepository,
            homeEditState = homeEditState,
            isClosedByMode = { mode -> mode != HomeEditMode.None },
            onTargetLost = ::close
        )
    }

    override fun startPicking(owner: Launchable) {
        if (homeEditState.mode.value != HomeEditMode.None) {
            return
        }
        session.onSheetOpened()
        packContents.value = null
        suggestionsJob?.cancel()
        suggestions.value = emptyList()
        request.value = IconPickerRequest(ownerKey = owner.key)
        loadSuggestions(owner = owner)
    }

    override fun openPack(iconPack: IconPack.CustomIconPack) {
        val currentRequest: IconPickerRequest = request.value ?: return
        if (currentRequest.packPackageName == iconPack.packageName) {
            return
        }
        request.value = currentRequest.copy(packPackageName = iconPack.packageName, query = "")
        packContents.value = null
        scope.launchLogged(message = "Failed to load an icon pack") {
            val categories: List<IconPackCategory> = iconRepository.getPackCategories(packPackageName = iconPack.packageName)
            if (request.value?.packPackageName == iconPack.packageName) {
                packContents.value = PackContents(packPackageName = iconPack.packageName, categories = categories)
            }
        }
    }

    override fun setQuery(query: String) {
        val currentRequest: IconPickerRequest = request.value ?: return
        if (currentRequest.packPackageName == null) {
            return
        }
        request.value = currentRequest.copy(query = query)
    }

    override fun selectIcon(icon: Icon.PackDrawableIcon) {
        val owner: Launchable = currentOwner() ?: return
        preferencesRepository.setCustomIcon(
            item = owner,
            icon = CustomIcon.PackDrawable(packPackageName = icon.packPackageName, drawableName = icon.drawableName)
        )
        close()
    }

    override fun resetIcon() {
        val owner: Launchable = currentOwner() ?: return
        preferencesRepository.resetCustomIcon(item = owner)
        close()
    }

    override fun cancelPicking() {
        close()
    }

    override fun onCardClosed() {
        close()
    }

    override fun onCardOpened() {
        close()
    }

    override fun onEditStarted() {
        close()
    }

    override fun onEditFinished() {
        close()
    }

    override fun backBeforeItemMenu(): Boolean {
        val currentRequest: IconPickerRequest = request.value ?: return false
        if (currentRequest.packPackageName == null) {
            close()
        } else {
            request.value = currentRequest.copy(packPackageName = null, query = "")
            packContents.value = null
        }
        return true
    }

    private fun currentOwner(): Launchable? {
        val ownerKey: String = request.value?.ownerKey ?: return null
        return launchableRepository.launchableState.value.launchables.findLaunchable(key = ownerKey) ?: run {
            close()
            null
        }
    }

    private fun loadSuggestions(owner: Launchable) {
        suggestionsJob = scope.launchLogged(message = "Failed to load icon suggestions") {
            iconRepository.getSuggestedIcons(launchable = owner).collect { suggestedIcons ->
                suggestions.value = suggestedIcons
            }
        }
    }

    private fun close() {
        suggestionsJob?.cancel()
        suggestionsJob = null
        request.value = null
        suggestions.value = emptyList()
        packContents.value = null
    }

    override fun release() {
        collaboratorScope.release()
    }
}

private fun filterPackIcons(categories: List<IconPackCategory>, query: String): List<Icon.PackDrawableIcon> {
    val normalizedQuery: String = normalizeIconPickerQuery(query)
    if (normalizedQuery.isEmpty()) {
        return emptyList()
    }
    return categories
        .flatMap { category -> category.icons }
        .filter { icon -> icon.drawableName.drawableNameContains(term = normalizedQuery) }
}
