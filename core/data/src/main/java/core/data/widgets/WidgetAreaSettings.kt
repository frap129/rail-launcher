package core.data.widgets

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Persisted preferences for the Widget Area and its stacks. */
@Entity(tableName = "widget_area_settings")
data class WidgetAreaSettingsRecord(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "max_stack_pages", defaultValue = "4") val maxStackPages: Int = WidgetAreaSettings.DEFAULT_MAX_STACK_PAGES,
    @ColumnInfo(name = "stack_indicator_visible", defaultValue = "1") val stackIndicatorVisible: Boolean = true,
    @ColumnInfo(name = "height_limit_enabled", defaultValue = "1") val heightLimitEnabled: Boolean = true,
    @ColumnInfo(name = "height_limit", defaultValue = "TWO_THIRDS") val heightLimit: WidgetAreaHeightLimit = WidgetAreaHeightLimit.DEFAULT,
    @ColumnInfo(name = "top_offset_dp", defaultValue = "0") val topOffsetDp: Int = 0,
    @ColumnInfo(name = "widget_spacing_dp", defaultValue = "16") val widgetSpacing: WidgetSpacing = WidgetSpacing.DEFAULT
) {
    /** Reads the row as a valid domain value, coercing a corrupt range back into bounds. */
    fun toDomain(): WidgetAreaSettings = WidgetAreaSettings(
        maxStackPages = maxStackPages.coerceIn(WidgetAreaSettings.MIN_STACK_PAGES, WidgetAreaSettings.MAX_STACK_PAGES),
        stackIndicatorVisible = stackIndicatorVisible,
        heightLimitEnabled = heightLimitEnabled,
        heightLimit = heightLimit,
        topOffsetDp = topOffsetDp.coerceAtLeast(0).let { offset ->
            WidgetAreaSettings.snapOffsetToStep(offsetDp = offset)
        },
        widgetSpacing = widgetSpacing
    )

    companion object {
        fun from(settings: WidgetAreaSettings): WidgetAreaSettingsRecord = WidgetAreaSettingsRecord(
            maxStackPages = settings.maxStackPages,
            stackIndicatorVisible = settings.stackIndicatorVisible,
            heightLimitEnabled = settings.heightLimitEnabled,
            heightLimit = settings.heightLimit,
            topOffsetDp = settings.topOffsetDp,
            widgetSpacing = settings.widgetSpacing
        )
    }
}

/** The User's Widget Area preferences. */
data class WidgetAreaSettings(
    val maxStackPages: Int = DEFAULT_MAX_STACK_PAGES,
    val stackIndicatorVisible: Boolean = true,
    val heightLimitEnabled: Boolean = true,
    val heightLimit: WidgetAreaHeightLimit = WidgetAreaHeightLimit.DEFAULT,
    val topOffsetDp: Int = 0,
    val widgetSpacing: WidgetSpacing = WidgetSpacing.DEFAULT
) {
    init {
        require(maxStackPages in MIN_STACK_PAGES..MAX_STACK_PAGES) {
            "maxStackPages must be between $MIN_STACK_PAGES and $MAX_STACK_PAGES"
        }
        require(topOffsetDp >= 0 && topOffsetDp % OFFSET_STEP_DP == 0) {
            "topOffsetDp must be a non-negative multiple of $OFFSET_STEP_DP"
        }
    }

    companion object {
        const val MIN_STACK_PAGES: Int = 2
        const val MAX_STACK_PAGES: Int = 8
        const val DEFAULT_MAX_STACK_PAGES: Int = 4
        const val OFFSET_STEP_DP: Int = 8

        fun snapOffsetToStep(offsetDp: Int): Int = (offsetDp.coerceAtLeast(0) + OFFSET_STEP_DP / 2) / OFFSET_STEP_DP * OFFSET_STEP_DP

        fun maxOffsetDp(homeHeightDp: Int): Int = (homeHeightDp / 2) / OFFSET_STEP_DP * OFFSET_STEP_DP

        val Default: WidgetAreaSettings = WidgetAreaSettings()
    }
}
