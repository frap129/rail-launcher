package core.ui.composables

import android.content.Context
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.focusable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.ui.model.data.hasShortcutModifier

private val rowFocusCornerRadius: Dp = 12.dp
private val rowFocusRingWidth: Dp = 2.dp
private const val ROW_FOCUS_BACKGROUND_ALPHA: Float = 0.16f

/** The keys that open the focused row, as a tap would. */
private val rowOpenKeys: Set<Key> = setOf(Key.Enter, Key.NumPadEnter, Key.DirectionCenter)

/**
 * Paints the focus indicator on any row that takes Row focus, including a clickable row such as a
 * Private Space action, and moves TalkBack's accessibility focus to it so both focuses stay on the
 * same row.
 */
@Composable
fun Modifier.rowFocusIndicator(): Modifier {
    val focusColor: Color = MaterialTheme.colorScheme.primary
    val context: Context = LocalContext.current
    val view: View = LocalView.current
    var isFocused: Boolean by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focusState ->
            isFocused = focusState.isFocused
            if (focusState.isFocused) {
                view.post { view.requestRowAccessibilityFocus(context = context) }
            }
        }
        .drawBehind { if (isFocused) drawRowFocusBackground(color = focusColor) }
        .drawWithContent {
            drawContent()
            if (isFocused) drawRowFocusRing(color = focusColor)
        }
}

/**
 * Makes this launcher row part of the hardware keyboard's Row focus: the arrow keys move it
 * between rows, Enter opens it as a tap would, and the focused row paints the focus indicator and
 * takes TalkBack's accessibility focus. Modified keys pass through, and Row focus never counts as
 * text-field focus, so Keyboard shortcuts and Typed characters still fire while a row has focus.
 */
@Composable
fun Modifier.tracksRowFocus(onOpen: () -> Unit): Modifier = this
    .rowFocusIndicator()
    .focusable()
    .onKeyEvent { event ->
        if (event.type != KeyEventType.KeyUp || event.key !in rowOpenKeys || event.nativeKeyEvent.hasShortcutModifier()) {
            false
        } else {
            onOpen()
            true
        }
    }

/** Moves the window's accessibility focus to the node Compose just gave input focus. */
private fun View.requestRowAccessibilityFocus(context: Context) {
    val accessibilityManager: AccessibilityManager = context.getSystemService(AccessibilityManager::class.java) ?: return
    if (!accessibilityManager.isEnabled || !accessibilityManager.isTouchExplorationEnabled) {
        return
    }
    val composeRoot: RootForTest = this as? RootForTest ?: return
    val focusedNode: SemanticsNode = composeRoot.semanticsOwner.rootSemanticsNode.findFocusedNode() ?: return
    accessibilityNodeProvider?.performAction(focusedNode.id, AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
}

/** The node the focus system gave input focus, searched from the semantics root. */
private fun SemanticsNode.findFocusedNode(): SemanticsNode? = if (config.getOrNull(SemanticsProperties.Focused) == true) {
    this
} else {
    children.firstNotNullOfOrNull { child -> child.findFocusedNode() }
}

/** Paints the tinted background a row with Row focus shows under its content. */
private fun DrawScope.drawRowFocusBackground(color: Color) {
    drawRoundRect(
        color = color.copy(alpha = ROW_FOCUS_BACKGROUND_ALPHA),
        cornerRadius = CornerRadius(x = rowFocusCornerRadius.toPx())
    )
}

/** Paints the ring a row with Row focus shows over its content. */
private fun DrawScope.drawRowFocusRing(color: Color) {
    val ringWidth: Float = rowFocusRingWidth.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(x = ringWidth / 2f, y = ringWidth / 2f),
        size = Size(width = size.width - ringWidth, height = size.height - ringWidth),
        cornerRadius = CornerRadius(x = rowFocusCornerRadius.toPx()),
        style = Stroke(width = ringWidth)
    )
}
