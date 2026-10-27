package core.ui.popups

/** The editor, pickers and Folder actions one Pop-up host owns, attached to its session. */
interface IPopupCollaborators {
    val editor: IPopupEditor

    val picker: IPopupPicker

    val folder: IPopupFolderActions

    /** Attaches the host's card to the session. */
    fun attachCard(card: IPopupCard)

    /** Releases the session and everything attached to it. */
    fun release()
}

/** Attaches its collaborators to [session] on creation and releases them on [release]. */
class IPopupCollaboratorsImpl(
    private val session: IPopupSession,
    override val editor: IPopupEditor,
    override val picker: IPopupPicker,
    override val folder: IPopupFolderActions
) : IPopupCollaborators {
    init {
        session.attach(editor = editor, picker = picker, folder = folder)
    }

    override fun attachCard(card: IPopupCard) {
        session.attach(card = card)
    }

    override fun release() {
        session.release()
    }
}
