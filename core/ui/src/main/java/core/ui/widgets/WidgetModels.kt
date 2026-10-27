package core.ui.widgets

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.prefs.ClockStyle
import core.data.prefs.HeaderAlignment
import core.data.prefs.HeaderSize
import core.data.widgets.WidgetAvailability
import core.ui.R

@Immutable
sealed interface WidgetModel {
    val id: Long
}

@Immutable
data class AppWidgetModel(
    override val id: Long,
    val appWidgetId: Int,
    val provider: String,
    val label: String,
    val icon: Icon,
    val minHeightDp: Int,
    val isWorkProfile: Boolean,
    val availability: WidgetAvailability
) : WidgetModel

@Immutable
data class HeaderModel(
    override val id: Long,
    val time: HeaderTimeModel?,
    val date: HeaderLineModel?,
    val battery: BatteryModel?,
    val events: List<EventModel>,
    val style: ClockStyle,
    val alignment: HeaderAlignment,
    val size: HeaderSize
) : WidgetModel

@Immutable
data class HeaderTimeModel(
    val text: String?,
    val hourText: String?,
    val minuteText: String?,
    val amPmText: String?,
    val spokenText: String,
    val tapTarget: HeaderTapTarget,
    @StringRes val clickLabel: Int?,
    val clickLabelArg: String? = null
)

@Immutable
data class HeaderLineModel(
    val text: String,
    val spokenText: String,
    val tapTarget: HeaderTapTarget,
    @StringRes val clickLabel: Int?,
    val clickLabelArg: String? = null
)

@Immutable
data class EventModel(
    val text: String,
    val suffix: String,
    val spokenText: String,
    val color: Color,
    val tapTarget: HeaderTapTarget,
    @StringRes val clickLabel: Int,
    val clickLabelArg: String
)

@Immutable
data class BatteryModel(val text: String, val percent: Int, val isCharging: Boolean, val isLow: Boolean) {
    @get:StringRes
    val contentDescriptionRes: Int
        get() = if (isCharging) R.string.battery_content_description_charging else R.string.battery_content_description
}

sealed interface HeaderTapTarget {
    data object SystemAlarms : HeaderTapTarget

    data object SystemBatteryUsage : HeaderTapTarget

    data class SystemCalendar(val atEpochMillis: Long) : HeaderTapTarget

    data class OpenEvent(val eventId: Long, val beginMillis: Long, val endMillis: Long, val isWorkProfile: Boolean) : HeaderTapTarget

    data class LaunchLaunchable(val launchable: Launchable) : HeaderTapTarget

    data object None : HeaderTapTarget
}
