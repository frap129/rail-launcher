package core.widgets

import androidx.annotation.StringRes
import core.data.launchables.model.Launchable
import core.data.prefs.HeaderSettings
import core.data.prefs.HeaderTapSetting
import core.data.prefs.isStale

class IHeaderTapTargetResolverImpl : IHeaderTapTargetResolver {
    override fun applyTo(header: HeaderModel, settings: HeaderSettings, launchables: List<Launchable>, atEpochMillis: Long): HeaderModel =
        header.copy(
            time = header.time?.withTapTarget(resolveTimeTapTarget(settings.timeTapTarget, launchables)),
            date = header.date?.withTapTarget(resolveDateTapTarget(settings.dateTapTarget, launchables, atEpochMillis))
        )

    override fun resolveSetting(setting: HeaderTapSetting, launchables: List<Launchable>, isLoaded: Boolean): HeaderTapSetting {
        if (!isLoaded) {
            return setting
        }
        val validKeys: Set<String> = launchables.mapTo(mutableSetOf()) { launchable -> launchable.key }
        return if (setting.isStale(validKeys = validKeys)) HeaderTapSetting.SystemDefault else setting
    }

    private fun resolveTimeTapTarget(setting: HeaderTapSetting?, launchables: List<Launchable>): HeaderTapTarget =
        resolveTapTarget(setting = setting, launchables = launchables, fallback = HeaderTapTarget.SystemAlarms)

    private fun resolveDateTapTarget(setting: HeaderTapSetting?, launchables: List<Launchable>, atEpochMillis: Long): HeaderTapTarget =
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

    @StringRes
    private fun HeaderTapTarget.clickLabel(): Int? = when (this) {
        HeaderTapTarget.SystemAlarms -> R.string.open_alarms
        is HeaderTapTarget.SystemCalendar -> R.string.open_calendar
        is HeaderTapTarget.OpenEvent -> R.string.open_event
        HeaderTapTarget.SystemBatteryUsage -> R.string.open_battery_usage
        is HeaderTapTarget.LaunchLaunchable -> R.string.open_launchable
        HeaderTapTarget.None -> null
    }

    private fun HeaderTapTarget.clickLabelArg(): String? = (this as? HeaderTapTarget.LaunchLaunchable)?.launchable?.name
}
