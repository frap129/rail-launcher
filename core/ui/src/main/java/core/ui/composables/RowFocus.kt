package core.ui.composables

import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusState
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
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.layout.Remeasurement
import androidx.compose.ui.layout.RemeasurementModifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeProviderCompat
import core.ui.model.data.hasShortcutModifier
import timber.log.Timber

private val rowFocusCornerRadius: Dp = 12.dp
private val rowFocusRingWidth: Dp = 2.dp
internal const val ROW_FOCUS_BACKGROUND_ALPHA: Float = 0.16f
const val ROW_HOVER_BACKGROUND_ALPHA: Float = 0.08f

/** The keys that open the focused row, as a tap would. */
private val rowOpenKeys: Set<Key> = setOf(Key.Enter, Key.NumPadEnter, Key.DirectionCenter)

/** Tracks the semantics IDs of controls that take part in Row focus. */
private class RowFocusRegistry {
    private val semanticsIds: MutableSet<Int> = mutableSetOf()

    fun register(semanticsId: Int) {
        semanticsIds += semanticsId
    }

    fun unregister(semanticsId: Int) {
        semanticsIds -= semanticsId
    }

    fun isRegistered(semanticsId: Int): Boolean = semanticsIds.contains(semanticsId)
}

/** Registry shared by Row focus modifiers and the accessibility node provider. */
private val LocalRowFocusRegistry: ProvidableCompositionLocal<RowFocusRegistry?> = staticCompositionLocalOf { null }

/** Holds the public layout information and current semantics ID for the row this modifier is attached to. */
private class RowFocusLayoutInfo : RemeasurementModifier {
    var layoutInfo: LayoutInfo? = null
        private set
    var semanticsId: Int? by mutableStateOf(null)
        private set

    override fun onRemeasurementAvailable(remeasurement: Remeasurement) {
        val updatedLayoutInfo: LayoutInfo = remeasurement as LayoutInfo
        layoutInfo = updatedLayoutInfo
        semanticsId = updatedLayoutInfo.semanticsId
    }
}

/**
 * Paints the focus indicator on a focusable control that is not a launcher row, such as an icon
 * button, and moves TalkBack's accessibility focus to it so both focuses stay on the same control.
 */
@Composable
fun Modifier.rowFocusIndicator(): Modifier {
    var isFocused: Boolean by remember { mutableStateOf(false) }
    return this
        .tracksRowFocusState(isRowFocusEnabled = false, onFocusChange = { isFocused = it })
        .rowBackground(isActive = isFocused, alpha = ROW_FOCUS_BACKGROUND_ALPHA)
        .rowFocusRing(isFocused = isFocused)
}

/**
 * Paints a launcher row's hover and Row focus states and moves TalkBack's accessibility focus to
 * the row when it takes Row focus. A focused row paints the focus background and ring, a hovered row
 * paints the hover background, and a row that is both paints the focus background only, so hover
 * never stacks under focus. Only rows with [isRowFocusEnabled] synchronize TalkBack focus back to
 * Row focus.
 */
@Composable
fun Modifier.rowHoverAndFocusIndicator(isRowFocusEnabled: Boolean): Modifier {
    val interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
    val isHovered: Boolean by interactionSource.collectIsHoveredAsState()
    var isFocused: Boolean by remember { mutableStateOf(false) }
    val backgroundAlpha: Float = if (isFocused) ROW_FOCUS_BACKGROUND_ALPHA else ROW_HOVER_BACKGROUND_ALPHA
    return this
        .hoverable(interactionSource = interactionSource)
        .tracksRowFocusState(isRowFocusEnabled = isRowFocusEnabled, onFocusChange = { isFocused = it })
        .rowBackground(isActive = isFocused || isHovered, alpha = backgroundAlpha)
        .rowFocusRing(isFocused = isFocused)
}

/** Paints the themed row background while [isActive], at [alpha] of the theme primary. */
@Composable
private fun Modifier.rowBackground(isActive: Boolean, alpha: Float): Modifier {
    val backgroundColor: Color = MaterialTheme.colorScheme.primary
    return drawBehind { if (isActive) drawRowBackground(color = backgroundColor, alpha = alpha) }
}

/** Paints the focus ring over the row's content while [isFocused]. */
@Composable
private fun Modifier.rowFocusRing(isFocused: Boolean): Modifier {
    val ringColor: Color = MaterialTheme.colorScheme.primary
    return drawWithContent {
        drawContent()
        if (isFocused) drawRowFocusRing(color = ringColor)
    }
}

/** Tracks this row's focus state and moves TalkBack's accessibility focus to it when it gains focus. */
@Composable
private fun Modifier.tracksRowFocusState(isRowFocusEnabled: Boolean, onFocusChange: (Boolean) -> Unit): Modifier {
    val view: View = LocalView.current
    val rowFocusRegistry: RowFocusRegistry? = LocalRowFocusRegistry.current
    val rowLayoutInfo: RowFocusLayoutInfo = remember { RowFocusLayoutInfo() }
    val semanticsId: Int? = rowLayoutInfo.semanticsId
    if (isRowFocusEnabled && rowFocusRegistry != null && semanticsId != null) {
        DisposableEffect(rowFocusRegistry, semanticsId) {
            rowFocusRegistry.register(semanticsId)
            onDispose { rowFocusRegistry.unregister(semanticsId) }
        }
    }
    return this
        .then(rowLayoutInfo)
        .onFocusChanged { focusState: FocusState ->
            onFocusChange(focusState.isFocused)
            if (focusState.isFocused) {
                view.post { view.requestRowAccessibilityFocus(rowLayoutInfo = rowLayoutInfo) }
            }
        }
}

/**
 * Makes this launcher row part of the hardware keyboard's Row focus: the arrow keys move it
 * between rows and Enter opens it as a tap would. The row paints [rowHoverAndFocusIndicator].
 * Modified keys pass through, and Row focus never counts as text-field focus, so Keyboard
 * shortcuts and Typed characters still fire while a row has focus.
 */
@Composable
fun Modifier.tracksRowFocus(onOpen: () -> Unit): Modifier = this
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
private fun View.requestRowAccessibilityFocus(rowLayoutInfo: RowFocusLayoutInfo) {
    val layoutInfo: LayoutInfo = rowLayoutInfo.layoutInfo?.takeIf { attachedInfo: LayoutInfo -> attachedInfo.isAttached } ?: return
    accessibilityNodeProvider?.performAction(layoutInfo.semanticsId, AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
}

/** Installs keyboard focus synchronization for [content] when TalkBack moves accessibility focus. */
@Composable
fun synchronizeRowAccessibilityFocus(content: @Composable () -> Unit) {
    val view: View = LocalView.current
    val rowFocusRegistry: RowFocusRegistry = remember { RowFocusRegistry() }
    val originalDelegate: AccessibilityDelegateCompat? = ViewCompat.getAccessibilityDelegate(view)
    val originalProvider: AccessibilityNodeProvider? = view.accessibilityNodeProvider
    if (originalProvider == null) {
        Timber.w("Unable to synchronize Row accessibility focus")
    } else {
        val accessibilityDelegate: AccessibilityDelegateCompat = remember(view, originalProvider) {
            object : AccessibilityDelegateCompat() {
                private val provider: AccessibilityNodeProviderCompat = AccessibilityNodeProviderCompat(
                    RowAccessibilityNodeProvider(delegate = originalProvider, rowFocusRegistry = rowFocusRegistry)
                )

                override fun getAccessibilityNodeProvider(host: View): AccessibilityNodeProviderCompat = provider
            }
        }
        DisposableEffect(view, accessibilityDelegate) {
            ViewCompat.setAccessibilityDelegate(view, accessibilityDelegate)
            onDispose {
                if (ViewCompat.getAccessibilityDelegate(view) === accessibilityDelegate) {
                    ViewCompat.setAccessibilityDelegate(view, originalDelegate)
                }
            }
        }
    }
    CompositionLocalProvider(LocalRowFocusRegistry provides rowFocusRegistry) {
        content()
    }
}

/** Keeps TalkBack's accessibility focus actions aligned with Compose's Row focus. */
private class RowAccessibilityNodeProvider(
    private val delegate: AccessibilityNodeProvider,
    private val rowFocusRegistry: RowFocusRegistry
) : AccessibilityNodeProvider() {
    override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? =
        delegate.createAccessibilityNodeInfo(virtualViewId)

    override fun performAction(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
        val actionPerformed: Boolean = delegate.performAction(virtualViewId, action, arguments)
        if (
            action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS &&
            actionPerformed &&
            rowFocusRegistry.isRegistered(virtualViewId)
        ) {
            val isInputFocused: Boolean = delegate.createAccessibilityNodeInfo(virtualViewId)?.isFocused == true
            if (!isInputFocused) {
                delegate.performAction(virtualViewId, AccessibilityNodeInfo.ACTION_FOCUS, null)
            }
        }
        return actionPerformed
    }

    override fun findFocus(focus: Int): AccessibilityNodeInfo? = delegate.findFocus(focus)

    override fun addExtraDataToAccessibilityNodeInfo(
        virtualViewId: Int,
        info: AccessibilityNodeInfo,
        extraDataKey: String,
        arguments: Bundle?
    ) {
        delegate.addExtraDataToAccessibilityNodeInfo(virtualViewId, info, extraDataKey, arguments)
    }
}

/** Paints the tinted background a row shows under its content while it is hovered or focused. */
internal fun DrawScope.drawRowBackground(color: Color, alpha: Float) {
    drawRoundRect(
        color = color.copy(alpha = alpha),
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
