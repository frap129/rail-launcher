package core.ui.widgets

import androidx.annotation.StringRes
import core.data.launchables.LaunchableState
import core.data.prefs.HeaderSettings
import core.data.prefs.HeaderTapSetting
import core.data.prefs.isDisplayable
import core.ui.R

class IHeaderTapTargetResolverImpl : IHeaderTapTargetResolver {
    override fun applyTo(
        header: HeaderModel,
        settings: HeaderSettings,
        launchableState: LaunchableState,
        atEpochMillis: Long
    ): HeaderModel = header.copy(
        time = header.time?.withTapTarget(resolveTimeTapTarget(settings.timeTapTarget, launchableState)),
        date = header.date?.withTapTarget(resolveDateTapTarget(settings.dateTapTarget, launchableState, atEpochMillis))
    )

    override fun resolveSetting(setting: HeaderTapSetting, launchableState: LaunchableState): HeaderTapSetting =
        if (setting.isDisplayable(state = launchableState)) setting else HeaderTapSetting.SystemDefault

    private fun resolveTimeTapTarget(setting: HeaderTapSetting?, launchableState: LaunchableState): HeaderTapTarget =
        resolveTapTarget(setting = setting, launchableState = launchableState, fallback = HeaderTapTarget.SystemAlarms)

    private fun resolveDateTapTarget(setting: HeaderTapSetting?, launchableState: LaunchableState, atEpochMillis: Long): HeaderTapTarget =
        resolveTapTarget(
            setting = setting,
            launchableState = launchableState,
            fallback = HeaderTapTarget.SystemCalendar(atEpochMillis = atEpochMillis)
        )

    private fun resolveTapTarget(setting: HeaderTapSetting?, launchableState: LaunchableState, fallback: HeaderTapTarget): HeaderTapTarget =
        when (setting) {
            null, HeaderTapSetting.SystemDefault -> fallback
            HeaderTapSetting.Nothing -> HeaderTapTarget.None
            is HeaderTapSetting.Launchable -> launchableTarget(setting = setting, launchableState = launchableState) ?: fallback
        }

    private fun launchableTarget(setting: HeaderTapSetting.Launchable, launchableState: LaunchableState): HeaderTapTarget? {
        if (!setting.isDisplayable(state = launchableState)) {
            return null
        }
        return launchableState.launchables.firstOrNull { launchable -> launchable.key == setting.key }
            ?.let { launchable -> HeaderTapTarget.LaunchLaunchable(launchable = launchable) }
    }

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
