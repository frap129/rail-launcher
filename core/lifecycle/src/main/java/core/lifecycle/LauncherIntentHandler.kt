package core.lifecycle

import android.content.Intent
import android.os.Bundle
import core.ui.model.data.LauncherSignal

/** The saved-state key of the creation intent already handled by a previous instance. */
private const val HANDLED_CREATION_INTENT_KEY: String = "handledCreationIntent"

/**
 * Turns launcher activity intents into launcher signals and remembers which creation intent was
 * handled, so a configuration change never handles the same creation intent twice.
 */
class LauncherIntentHandler(private val onSignal: (LauncherSignal) -> Unit) {
    private var handledCreationIntentToken: String? = null

    /** Restores the creation intent already handled by the previous instance. */
    fun restore(savedInstanceState: Bundle?) {
        handledCreationIntentToken = savedInstanceState?.getString(HANDLED_CREATION_INTENT_KEY)
    }

    /** Saves the creation intent already handled for a recreated instance. */
    fun save(outState: Bundle) {
        outState.putString(HANDLED_CREATION_INTENT_KEY, handledCreationIntentToken)
    }

    /**
     * Signals the launcher intent [intent] carries and returns whether it was handled. A creation
     * intent only handles the Search entry, while HOME intents arrive through [isNewIntent].
     */
    fun handle(intent: Intent, wasResumed: Boolean, isNewIntent: Boolean): Boolean {
        val token: String = intent.creationToken()
        if (!isNewIntent && token == handledCreationIntentToken) {
            return false
        }
        val signal: LauncherSignal = when {
            intent.isSearchEntry() -> LauncherSignal.OpenSearch
            isNewIntent && intent.isHomeIntent() -> LauncherSignal.Home(wasResumed = wasResumed)
            else -> return false
        }
        onSignal(signal)
        if (!isNewIntent) {
            handledCreationIntentToken = token
        }
        return true
    }
}

private fun Intent.creationToken(): String = "${action.orEmpty()}|${dataString.orEmpty()}"

private fun Intent.isHomeIntent(): Boolean = action == Intent.ACTION_MAIN && hasCategory(Intent.CATEGORY_HOME)
