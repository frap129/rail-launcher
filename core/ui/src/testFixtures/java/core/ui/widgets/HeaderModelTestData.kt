package core.ui.widgets

import core.data.prefs.ClockStyle
import core.data.prefs.HeaderAlignment
import core.data.prefs.HeaderSize

fun createHeaderModel(
    id: Long,
    timeText: String,
    spokenText: String = timeText,
    tapTarget: HeaderTapTarget = HeaderTapTarget.None,
    clickLabel: Int? = null,
    date: HeaderLineModel? = null,
    style: ClockStyle = ClockStyle.CLASSIC,
    alignment: HeaderAlignment = HeaderAlignment.START,
    size: HeaderSize = HeaderSize.DEFAULT
): HeaderModel = HeaderModel(
    id = id,
    time = HeaderTimeModel(
        text = timeText,
        hourText = null,
        minuteText = null,
        amPmText = null,
        spokenText = spokenText,
        tapTarget = tapTarget,
        clickLabel = clickLabel
    ),
    date = date,
    battery = null,
    events = emptyList(),
    style = style,
    alignment = alignment,
    size = size
)
