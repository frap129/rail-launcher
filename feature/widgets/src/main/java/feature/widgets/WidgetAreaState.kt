package feature.widgets

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

/** What the Widget Area shows once the first clock reading has arrived. */
@Immutable
data class WidgetAreaState(val places: List<WidgetPlaceModel> = emptyList(), val hasWidgets: Boolean = places.isNotEmpty())

@Immutable
data class WidgetPlaceModel(val id: Long, val widgets: List<WidgetModel>)

@Immutable
sealed interface WidgetModel {
    val id: Long
}

@Immutable
data class HeaderModel(override val id: Long, val time: HeaderLineModel?, val date: HeaderLineModel?) : WidgetModel

@Immutable
data class HeaderLineModel(val text: String, val spokenText: String, val tapTarget: HeaderTapTarget, @StringRes val clickLabel: Int)

sealed interface HeaderTapTarget {
    data object SystemAlarms : HeaderTapTarget

    data class SystemCalendar(val atEpochMillis: Long) : HeaderTapTarget
}
