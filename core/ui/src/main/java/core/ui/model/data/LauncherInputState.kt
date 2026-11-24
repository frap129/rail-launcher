package core.ui.model.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Identifies one text field whose focus [LauncherInputState] counts. */
class TextFieldFocusToken

/**
 * The launcher screen's input state while it is shown: whether it accepts typed characters, whether
 * Tab may toggle the App List, and whether any text field has focus so Keyboard shortcuts stay out
 * of their way.
 *
 * A text field reports focus with a token of its own; the launcher counts the tokens, so moving
 * focus from one text field to another keeps shortcuts off while a field that leaves the screen
 * drops its token on its own.
 */
class LauncherInputState {
    private val mutableAcceptsTyping: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val mutableCanToggleAppList: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val mutableIsTextFieldFocused: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val focusedTextFieldTokens: MutableSet<TextFieldFocusToken> = mutableSetOf()

    val acceptsTyping: StateFlow<Boolean> = mutableAcceptsTyping

    val canToggleAppList: StateFlow<Boolean> = mutableCanToggleAppList

    val isTextFieldFocused: StateFlow<Boolean> = mutableIsTextFieldFocused

    fun setAcceptsTyping(acceptsTyping: Boolean) {
        mutableAcceptsTyping.value = acceptsTyping
    }

    fun setCanToggleAppList(canToggleAppList: Boolean) {
        mutableCanToggleAppList.value = canToggleAppList
    }

    fun setTextFieldFocused(token: TextFieldFocusToken, isFocused: Boolean) {
        if (isFocused) {
            focusedTextFieldTokens += token
        } else {
            focusedTextFieldTokens -= token
        }
        mutableIsTextFieldFocused.value = focusedTextFieldTokens.isNotEmpty()
    }
}
