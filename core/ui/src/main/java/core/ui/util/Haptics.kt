package core.ui.util

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/** Performs the launcher's standard long-press haptic. */
fun HapticFeedback.performLongPressHaptic() {
    performHapticFeedback(HapticFeedbackType.LongPress)
}

/** Performs the launcher's standard reorder segment-tick haptic. */
fun HapticFeedback.performSegmentTickHaptic() {
    performHapticFeedback(HapticFeedbackType.SegmentTick)
}

/** Performs the Rail's standard scrub-tick haptic. */
fun HapticFeedback.performRailTickHaptic() {
    performHapticFeedback(HapticFeedbackType.TextHandleMove)
}

/** Performs the firm haptic that confirms a Home swipe has armed its action. */
fun HapticFeedback.performHomeSwipeHaptic() {
    performHapticFeedback(HapticFeedbackType.Confirm)
}
