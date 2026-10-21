package feature.widgets

import androidx.annotation.StringRes
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Check
import core.data.prefs.ClockStyle
import core.data.prefs.HeaderAlignment
import core.ui.composables.OutlinedText

private val minTouchTargetHeight: Dp = 48.dp
private val previewSpacing: Dp = 8.dp
private val previewPadding: Dp = 20.dp
private val selectedIconSize: Dp = 24.dp
private const val STACKED_LINE_HEIGHT_MULTIPLIER: Float = 0.9f
private const val COMPACT_SEPARATOR: String = "·"

@Composable
fun HeaderContent(
    model: HeaderModel,
    menuEnabled: Boolean,
    onTapTarget: (HeaderTapTarget) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true
) {
    if (model.style == ClockStyle.COMPACT) {
        HeaderCompactContent(
            model = model,
            menuEnabled = menuEnabled,
            interactive = interactive,
            onTapTarget = onTapTarget,
            onLongPress = onLongPress,
            modifier = modifier
        )
        return
    }
    val isBold: Boolean = model.style == ClockStyle.BOLD
    val timeStyle: TextStyle = MaterialTheme.typography.displayLarge
        .scaled(model.size.timeMultiplier)
        .copy(fontWeight = if (isBold) FontWeight.Black else FontWeight.Normal)
    val secondaryStyle: TextStyle = MaterialTheme.typography.titleMedium
        .scaled(model.size.secondaryMultiplier)
        .copy(fontWeight = if (isBold) FontWeight.SemiBold else FontWeight.Normal)
    Column(modifier = modifier.fillMaxWidth()) {
        model.time?.let { time ->
            if (model.style == ClockStyle.STACKED) {
                StackedTime(
                    time = time,
                    timeStyle = timeStyle,
                    secondaryStyle = secondaryStyle,
                    alignment = model.alignment,
                    menuEnabled = menuEnabled,
                    interactive = interactive,
                    onTapTarget = onTapTarget,
                    onLongPress = onLongPress
                )
            } else {
                HeaderTimeText(
                    time = time,
                    style = timeStyle,
                    alignment = model.alignment,
                    menuEnabled = menuEnabled,
                    interactive = interactive,
                    onTapTarget = onTapTarget,
                    onLongPress = onLongPress
                )
            }
        }
        model.date?.let { date ->
            HeaderTapTargetText(
                line = date,
                style = secondaryStyle,
                alignment = model.alignment,
                menuEnabled = menuEnabled,
                interactive = interactive,
                onTapTarget = onTapTarget,
                onLongPress = onLongPress
            )
        }
    }
}

@StringRes
fun ClockStyle.labelResource(): Int = when (this) {
    ClockStyle.CLASSIC -> R.string.clock_style_classic
    ClockStyle.BOLD -> R.string.clock_style_bold
    ClockStyle.STACKED -> R.string.clock_style_stacked
    ClockStyle.COMPACT -> R.string.clock_style_compact
}

@Composable
fun HeaderClockStyleChooser(
    selected: ClockStyle,
    previews: List<HeaderModel>,
    onSelect: (ClockStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.clock_style_title),
            modifier = Modifier.padding(horizontal = previewPadding, vertical = 10.dp),
            style = MaterialTheme.typography.headlineMedium
        )
        previews.forEach { preview ->
            val isSelected: Boolean = preview.style == selected
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(preview.style) })
                    .padding(horizontal = previewPadding, vertical = 10.dp)
            ) {
                HeaderContent(
                    model = preview,
                    menuEnabled = false,
                    onTapTarget = {},
                    onLongPress = {},
                    interactive = false
                )
                Spacer(modifier = Modifier.size(previewSpacing))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = stringResource(preview.style.labelResource()), style = MaterialTheme.typography.titleMedium)
                    if (isSelected) {
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            modifier = Modifier.size(selectedIconSize),
                            imageVector = FontAwesomeIcons.Solid.Check,
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderTimeText(
    time: HeaderTimeModel,
    style: TextStyle,
    alignment: HeaderAlignment,
    menuEnabled: Boolean,
    interactive: Boolean,
    onTapTarget: (HeaderTapTarget) -> Unit,
    onLongPress: () -> Unit
) {
    val text: String = time.text ?: return
    val clickLabel: String? = resolveClickLabel(clickLabel = time.clickLabel, arg = time.clickLabelArg)
    HeaderTapTargetBox(
        clickLabel = clickLabel,
        spokenText = time.spokenText,
        tapTarget = time.tapTarget,
        alignment = alignment,
        menuEnabled = menuEnabled,
        interactive = interactive,
        onTapTarget = onTapTarget,
        onLongPress = onLongPress
    ) {
        OutlinedText(text = text, style = style, textAlign = alignment.textAlign())
    }
}

@Composable
private fun StackedTime(
    time: HeaderTimeModel,
    timeStyle: TextStyle,
    secondaryStyle: TextStyle,
    alignment: HeaderAlignment,
    menuEnabled: Boolean,
    interactive: Boolean,
    onTapTarget: (HeaderTapTarget) -> Unit,
    onLongPress: () -> Unit
) {
    val hourText: String = time.hourText ?: return
    val minuteText: String = time.minuteText ?: return
    val clickLabel: String? = resolveClickLabel(clickLabel = time.clickLabel, arg = time.clickLabelArg)
    val stackedStyle: TextStyle = timeStyle.copy(lineHeight = timeStyle.fontSize * STACKED_LINE_HEIGHT_MULTIPLIER)
    HeaderTapTargetBox(
        clickLabel = clickLabel,
        spokenText = time.spokenText,
        tapTarget = time.tapTarget,
        alignment = alignment,
        menuEnabled = menuEnabled,
        interactive = interactive,
        onTapTarget = onTapTarget,
        onLongPress = onLongPress
    ) {
        Column(horizontalAlignment = alignment.horizontalAlignment()) {
            OutlinedText(text = hourText, style = stackedStyle, textAlign = alignment.textAlign())
            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedText(text = minuteText, style = stackedStyle, textAlign = alignment.textAlign())
                time.amPmText?.let { amPmText ->
                    OutlinedText(
                        text = amPmText,
                        modifier = Modifier.padding(start = 4.dp),
                        style = secondaryStyle,
                        textAlign = alignment.textAlign()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderCompactContent(
    model: HeaderModel,
    menuEnabled: Boolean,
    interactive: Boolean,
    onTapTarget: (HeaderTapTarget) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val time: HeaderTimeModel? = model.time
    val timeText: String? = time?.text
    if (timeText == null && model.date == null) {
        return
    }
    val timeStyle: TextStyle = MaterialTheme.typography.headlineMedium.scaled(model.size.timeMultiplier)
    val dateStyle: TextStyle = MaterialTheme.typography.titleMedium.scaled(model.size.secondaryMultiplier)
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(previewSpacing, model.alignment.horizontalAlignment()),
        itemVerticalAlignment = Alignment.Bottom
    ) {
        if (time != null && timeText != null) {
            HeaderTapTargetText(
                line = HeaderLineModel(
                    text = timeText,
                    spokenText = time.spokenText,
                    tapTarget = time.tapTarget,
                    clickLabel = time.clickLabel
                ),
                style = timeStyle,
                alignment = model.alignment,
                menuEnabled = menuEnabled,
                interactive = interactive,
                fillWidth = false,
                onTapTarget = onTapTarget,
                onLongPress = onLongPress
            )
        }
        model.date?.let { date ->
            Row(verticalAlignment = Alignment.Bottom) {
                if (timeText != null) {
                    Text(
                        text = COMPACT_SEPARATOR,
                        modifier = Modifier.padding(end = previewSpacing),
                        style = dateStyle
                    )
                }
                HeaderTapTargetText(
                    line = date,
                    style = dateStyle,
                    alignment = model.alignment,
                    menuEnabled = menuEnabled,
                    interactive = interactive,
                    fillWidth = false,
                    onTapTarget = onTapTarget,
                    onLongPress = onLongPress
                )
            }
        }
    }
}

@Composable
private fun HeaderTapTargetText(
    line: HeaderLineModel,
    style: TextStyle,
    alignment: HeaderAlignment,
    menuEnabled: Boolean,
    interactive: Boolean,
    onTapTarget: (HeaderTapTarget) -> Unit,
    onLongPress: () -> Unit,
    fillWidth: Boolean = true
) {
    val clickLabel: String? = resolveClickLabel(clickLabel = line.clickLabel, arg = line.clickLabelArg)
    HeaderTapTargetBox(
        clickLabel = clickLabel,
        spokenText = line.spokenText,
        tapTarget = line.tapTarget,
        alignment = alignment,
        menuEnabled = menuEnabled,
        interactive = interactive,
        onTapTarget = onTapTarget,
        onLongPress = onLongPress,
        fillWidth = fillWidth
    ) {
        OutlinedText(text = line.text, style = style, textAlign = alignment.textAlign())
    }
}

@Composable
private fun HeaderTapTargetBox(
    clickLabel: String?,
    spokenText: String,
    tapTarget: HeaderTapTarget,
    alignment: HeaderAlignment,
    menuEnabled: Boolean,
    interactive: Boolean,
    onTapTarget: (HeaderTapTarget) -> Unit,
    onLongPress: () -> Unit,
    fillWidth: Boolean = true,
    content: @Composable () -> Unit
) {
    val isClickable: Boolean = clickLabel != null
    val semanticsModifier: Modifier = if (interactive) {
        Modifier
            .clearAndSetSemantics {
                contentDescription = spokenText
                if (isClickable) {
                    onClick(label = clickLabel.orEmpty()) {
                        onTapTarget(tapTarget)
                        true
                    }
                } else if (menuEnabled) {
                    onLongClick {
                        onLongPress()
                        true
                    }
                }
            }
            .then(
                tapModifier(
                    isClickable = isClickable,
                    menuEnabled = menuEnabled,
                    clickLabel = clickLabel.orEmpty(),
                    tapTarget = tapTarget,
                    onTapTarget = onTapTarget,
                    onLongPress = onLongPress
                )
            )
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .defaultMinSize(minHeight = minTouchTargetHeight)
            .then(semanticsModifier),
        contentAlignment = alignment.contentAlignment()
    ) {
        content()
    }
}

private fun tapModifier(
    isClickable: Boolean,
    menuEnabled: Boolean,
    clickLabel: String,
    tapTarget: HeaderTapTarget,
    onTapTarget: (HeaderTapTarget) -> Unit,
    onLongPress: () -> Unit
): Modifier = when {
    isClickable -> Modifier.combinedClickable(
        onClickLabel = clickLabel,
        onLongClick = if (menuEnabled) onLongPress else null,
        onClick = { onTapTarget(tapTarget) }
    )

    menuEnabled -> Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }

    else -> Modifier.pointerInput(Unit) { detectTapGestures(onTap = {}) }
}

@Composable
private fun resolveClickLabel(clickLabel: Int?, arg: String?): String? = when {
    clickLabel == null -> null
    arg == null -> stringResource(clickLabel)
    else -> stringResource(clickLabel, arg)
}

private fun TextStyle.scaled(multiplier: Float): TextStyle = copy(
    fontSize = fontSize * multiplier,
    lineHeight = lineHeight * multiplier
)

private fun HeaderAlignment.contentAlignment(): Alignment = when (this) {
    HeaderAlignment.START -> Alignment.CenterStart
    HeaderAlignment.CENTER -> Alignment.Center
    HeaderAlignment.END -> Alignment.CenterEnd
}

private fun HeaderAlignment.horizontalAlignment(): Alignment.Horizontal = when (this) {
    HeaderAlignment.START -> Alignment.Start
    HeaderAlignment.CENTER -> Alignment.CenterHorizontally
    HeaderAlignment.END -> Alignment.End
}

private fun HeaderAlignment.textAlign(): TextAlign = when (this) {
    HeaderAlignment.START -> TextAlign.Start
    HeaderAlignment.CENTER -> TextAlign.Center
    HeaderAlignment.END -> TextAlign.End
}
