package feature.widgets

import androidx.annotation.StringRes
import core.data.launchables.model.Launchable
import core.data.prefs.HeaderSettings
import core.data.prefs.HeaderTapSetting

/** Rewrites a formatted Header's tap targets from the user's settings. */
internal fun HeaderModel.withTapTargets(settings: HeaderSettings, launchables: List<Launchable>, atEpochMillis: Long): HeaderModel = copy(
    time = time?.withTapTarget(resolveTimeTapTarget(settings.timeTapTarget, launchables)),
    date = date?.withTapTarget(resolveDateTapTarget(settings.dateTapTarget, launchables, atEpochMillis))
)

internal fun resolveTimeTapTarget(setting: HeaderTapSetting?, launchables: List<Launchable>): HeaderTapTarget =
    resolveTapTarget(setting = setting, launchables = launchables, fallback = HeaderTapTarget.SystemAlarms)

internal fun resolveDateTapTarget(setting: HeaderTapSetting?, launchables: List<Launchable>, atEpochMillis: Long): HeaderTapTarget =
    resolveTapTarget(
        setting = setting,
        launchables = launchables,
        fallback = HeaderTapTarget.SystemCalendar(atEpochMillis = atEpochMillis)
    )

private fun resolveTapTarget(setting: HeaderTapSetting?, launchables: List<Launchable>, fallback: HeaderTapTarget): HeaderTapTarget =
    when (setting) {
        null, HeaderTapSetting.SystemDefault -> fallback
        HeaderTapSetting.Nothing -> HeaderTapTarget.None
        is HeaderTapSetting.Launchable -> launchableTarget(setting = setting, launchables = launchables) ?: fallback
    }

@StringRes
internal fun HeaderTapTarget.clickLabel(): Int? = when (this) {
    HeaderTapTarget.SystemAlarms -> R.string.open_alarms
    is HeaderTapTarget.SystemCalendar -> R.string.open_calendar
    is HeaderTapTarget.OpenEvent -> R.string.open_event
    HeaderTapTarget.SystemBatteryUsage -> R.string.open_battery_usage
    is HeaderTapTarget.LaunchLaunchable -> R.string.open_launchable
    HeaderTapTarget.None -> null
}

internal fun HeaderTapTarget.clickLabelArg(): String? = (this as? HeaderTapTarget.LaunchLaunchable)?.launchable?.name

private fun launchableTarget(setting: HeaderTapSetting.Launchable, launchables: List<Launchable>): HeaderTapTarget? =
    launchables.firstOrNull { launchable -> launchable.key == setting.key }
        ?.let { launchable -> HeaderTapTarget.LaunchLaunchable(launchable = launchable) }

private fun HeaderTimeModel.withTapTarget(target: HeaderTapTarget): HeaderTimeModel = copy(
    tapTarget = target,
    clickLabel = target.clickLabel(),
    clickLabelArg = target.clickLabelArg()
)

private fun HeaderLineModel.withTapTarget(target: HeaderTapTarget): HeaderLineModel = copy(
    tapTarget = target,
    clickLabel = target.clickLabel(),
    clickLabelArg = target.clickLabelArg()
)
