package core.ui.widgets

import core.data.launchables.LaunchableState
import core.data.prefs.ClockStyle
import core.data.prefs.HeaderSettings
import core.data.prefs.HeaderTapSetting
import core.data.prefs.isDisplayable

class IHeaderTapTargetResolverImpl : IHeaderTapTargetResolver {
    override fun applyTo(
        header: HeaderModel,
        settings: HeaderSettings,
        launchableState: LaunchableState,
        atEpochMillis: Long
    ): HeaderModel = header.copy(
        time = header.time?.withTapTarget(
            resolveTimeTapTarget(
                setting = settings.timeTapTarget,
                launchableState = launchableState,
                clockStyle = settings.clockStyle
            )
        ),
        date = header.date?.withTapTarget(
            resolveDateTapTarget(
                setting = settings.dateTapTarget,
                launchableState = launchableState,
                atEpochMillis = atEpochMillis,
                clockStyle = settings.clockStyle
            )
        )
    )

    override fun resolveSetting(setting: HeaderTapSetting, launchableState: LaunchableState): HeaderTapSetting =
        if (setting.isDisplayable(state = launchableState)) setting else HeaderTapSetting.SystemDefault

    private fun resolveTimeTapTarget(
        setting: HeaderTapSetting?,
        launchableState: LaunchableState,
        clockStyle: ClockStyle
    ): HeaderTapTarget = resolveTapTarget(
        setting = setting,
        launchableState = launchableState,
        fallback = clockStyle.timeDefaultTapTarget()
    )

    private fun resolveDateTapTarget(
        setting: HeaderTapSetting?,
        launchableState: LaunchableState,
        atEpochMillis: Long,
        clockStyle: ClockStyle
    ): HeaderTapTarget = resolveTapTarget(
        setting = setting,
        launchableState = launchableState,
        fallback = clockStyle.dateDefaultTapTarget(atEpochMillis = atEpochMillis)
    )

    private fun resolveTapTarget(setting: HeaderTapSetting?, launchableState: LaunchableState, fallback: HeaderTapTarget): HeaderTapTarget =
        when (setting) {
            null, HeaderTapSetting.SystemDefault -> fallback
            HeaderTapSetting.Nothing -> HeaderTapTarget.None
            HeaderTapSetting.OpenAgenda -> HeaderTapTarget.OpenAgenda
            is HeaderTapSetting.Launchable -> launchableTarget(setting = setting, launchableState = launchableState) ?: fallback
        }

    private fun launchableTarget(setting: HeaderTapSetting.Launchable, launchableState: LaunchableState): HeaderTapTarget? {
        if (!setting.isDisplayable(state = launchableState)) {
            return null
        }
        return launchableState.launchableFor(key = setting.key)
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
}
