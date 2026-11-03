package feature.launcher

/** Shows the outcome of a Pin request add even after its dialog is gone. */
interface IPinShortcutNotifier {
    /** Shows the "Couldn't add shortcut" message. */
    fun showAddError()
}
