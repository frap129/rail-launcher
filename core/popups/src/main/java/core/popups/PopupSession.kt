package core.popups

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

/**
 * The layers and cross-cutting state one Pop-up host shares between its card, editor, pickers and
 * Folder actions. A host creates one session, passes it to every Pop-up ViewModel and attaches them
 * once; a host without a card leaves the card unattached, so card commands do nothing.
 */
interface IPopupSession {
    /** Changes whenever a quiet-mode resume asks the card and editor to re-read their Shortcuts. */
    val shortcutReloads: Flow<Int>

    /** Connects the host's collaborators. Call once per host after creating them. */
    fun attach(
        card: PopupCardViewModel? = null,
        editor: PopupEditorViewModel? = null,
        picker: PopupPickerViewModel? = null,
        folder: PopupFolderViewModel? = null
    )

    /** Clears the pickers an opening card would cover. */
    fun onCardOpened()

    /** Dismisses the editor, pickers and Folder sheets a closing card would cover. */
    fun onCardClosed()

    /** Closes what an edit sheet, picker or Folder dialog would cover. */
    fun onSheetOpened()

    /** Dismisses the pickers when the editor closes. */
    fun onEditFinished()

    /** Closes the card and its menu when [ownerKey]'s Folder is deleted. */
    fun onOwnerDeleted(ownerKey: String)

    /** The open card's owner key, or null when no card is open. */
    fun openOwnerKey(): String?

    /** Closes the topmost Pop-up layer, returning whether it consumed Back. */
    fun onBack(): Boolean

    /** Re-reads the card's and editor's Shortcuts, for example after a work profile resumes. */
    fun reloadShortcuts()
}

/** The one session each Pop-up host creates and hands to its ViewModels. */
class IPopupSessionImpl : IPopupSession {
    private var card: PopupCardViewModel? = null
    private var editor: PopupEditorViewModel? = null
    private var picker: PopupPickerViewModel? = null
    private var folder: PopupFolderViewModel? = null
    private val reloads: MutableStateFlow<Int> = MutableStateFlow(0)

    override val shortcutReloads: Flow<Int> = reloads

    override fun attach(
        card: PopupCardViewModel?,
        editor: PopupEditorViewModel?,
        picker: PopupPickerViewModel?,
        folder: PopupFolderViewModel?
    ) {
        card?.let { this.card = it }
        editor?.let { this.editor = it }
        picker?.let { this.picker = it }
        folder?.let { this.folder = it }
    }

    override fun onCardOpened() {
        picker?.onCardOpened()
    }

    override fun onCardClosed() {
        folder?.onCardClosed()
        picker?.onCardClosed()
        editor?.onCardClosed()
    }

    override fun onSheetOpened() {
        card?.closeMenu()
        folder?.onEditStarted()
        picker?.onEditStarted()
    }

    override fun onEditFinished() {
        picker?.onEditFinished()
    }

    override fun onOwnerDeleted(ownerKey: String) {
        card?.onOwnerDeleted(ownerKey = ownerKey)
        editor?.finishEditing(ownerKey = ownerKey)
    }

    override fun openOwnerKey(): String? {
        val openCard: PopupCardViewModel = card ?: run {
            Timber.w("Ignoring a card action without an attached Pop-up card")
            return null
        }
        return openCard.currentOpenOwnerKey()
    }

    override fun onBack(): Boolean {
        val openCard: PopupCardViewModel = card ?: run {
            Timber.w("Ignoring Back without an attached Pop-up card")
            return false
        }
        if (openCard.isResizing()) {
            openCard.finishResize()
            return true
        }
        if (folder?.backBeforeItemMenu() == true) {
            return true
        }
        if (picker?.backBeforeItemMenu() == true) {
            return true
        }
        if (openCard.isItemMenuOpen()) {
            openCard.closeMenu()
            return true
        }
        if (editor?.backEditor() == true) {
            return true
        }
        if (openCard.isMenuOpen()) {
            openCard.closeMenu()
            return true
        }
        if (!openCard.isOpen()) {
            return false
        }
        openCard.close()
        return true
    }

    override fun reloadShortcuts() {
        reloads.update { reload -> reload + 1 }
    }
}
