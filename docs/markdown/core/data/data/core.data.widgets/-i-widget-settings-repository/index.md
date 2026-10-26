//[data](../../../index.md)/[core.data.widgets](../index.md)/[IWidgetSettingsRepository](index.md)

# IWidgetSettingsRepository

interface [IWidgetSettingsRepository](index.md)

#### Inheritors

| |
|---|
| [IWidgetSettingsRepositoryImpl](../-i-widget-settings-repository-impl/index.md) |

## Properties

| Name | Summary |
|---|---|
| [eventSettings](event-settings.md) | [main]<br>abstract val [eventSettings](event-settings.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[EventSettings](../../core.data.prefs/-event-settings/index.md)&gt; |
| [headerSettings](header-settings.md) | [main]<br>abstract val [headerSettings](header-settings.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[HeaderSettings](../../core.data.prefs/-header-settings/index.md)&gt; |
| [mediaPlayerSettings](media-player-settings.md) | [main]<br>abstract val [mediaPlayerSettings](media-player-settings.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[MediaPlayerSettings](../-media-player-settings/index.md)&gt; |
| [widgetAreaSettings](widget-area-settings.md) | [main]<br>abstract val [widgetAreaSettings](widget-area-settings.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&lt;[WidgetAreaSettings](../-widget-area-settings/index.md)&gt; |

## Functions

| Name | Summary |
|---|---|
| [pruneExcludedCalendarIds](prune-excluded-calendar-ids.md) | [main]<br>open suspend fun [pruneExcludedCalendarIds](prune-excluded-calendar-ids.md)(availableCalendarIds: [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-set/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)&gt;) |
| [setBatteryVisibility](set-battery-visibility.md) | [main]<br>open suspend fun [setBatteryVisibility](set-battery-visibility.md)(visibility: [BatteryVisibility](../../core.data.prefs/-battery-visibility/index.md)) |
| [setClockVisible](set-clock-visible.md) | [main]<br>open suspend fun [setClockVisible](set-clock-visible.md)(visible: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)) |
| [setDateVisible](set-date-visible.md) | [main]<br>open suspend fun [setDateVisible](set-date-visible.md)(visible: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)) |
| [setEventIncludeAllDay](set-event-include-all-day.md) | [main]<br>open suspend fun [setEventIncludeAllDay](set-event-include-all-day.md)(includeAllDay: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)) |
| [setEventLookaheadHours](set-event-lookahead-hours.md) | [main]<br>open suspend fun [setEventLookaheadHours](set-event-lookahead-hours.md)(hours: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)) |
| [setEventMaxEvents](set-event-max-events.md) | [main]<br>open suspend fun [setEventMaxEvents](set-event-max-events.md)(maxEvents: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)) |
| [setEventsEnabled](set-events-enabled.md) | [main]<br>open suspend fun [setEventsEnabled](set-events-enabled.md)(enabled: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)) |
| [setExcludedCalendarIds](set-excluded-calendar-ids.md) | [main]<br>open suspend fun [setExcludedCalendarIds](set-excluded-calendar-ids.md)(calendarIds: [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-set/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)&gt;) |
| [setHeightLimit](set-height-limit.md) | [main]<br>open suspend fun [setHeightLimit](set-height-limit.md)(limit: [WidgetAreaHeightLimit](../-widget-area-height-limit/index.md)) |
| [setHeightLimitEnabled](set-height-limit-enabled.md) | [main]<br>open suspend fun [setHeightLimitEnabled](set-height-limit-enabled.md)(enabled: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)) |
| [setMaxStackPages](set-max-stack-pages.md) | [main]<br>open suspend fun [setMaxStackPages](set-max-stack-pages.md)(maxStackPages: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)) |
| [setMediaPlayerEnabled](set-media-player-enabled.md) | [main]<br>open suspend fun [setMediaPlayerEnabled](set-media-player-enabled.md)(enabled: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)) |
| [setMediaPlayerPackageExcluded](set-media-player-package-excluded.md) | [main]<br>open suspend fun [setMediaPlayerPackageExcluded](set-media-player-package-excluded.md)(packageName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), excluded: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)) |
| [setMediaPlayerPlacement](set-media-player-placement.md) | [main]<br>open suspend fun [setMediaPlayerPlacement](set-media-player-placement.md)(placement: [MediaPlayerPlacement](../-media-player-placement/index.md)) |
| [setMediaPlayerShowArtwork](set-media-player-show-artwork.md) | [main]<br>open suspend fun [setMediaPlayerShowArtwork](set-media-player-show-artwork.md)(showArtwork: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)) |
| [setMediaPlayerShowWhenPaused](set-media-player-show-when-paused.md) | [main]<br>open suspend fun [setMediaPlayerShowWhenPaused](set-media-player-show-when-paused.md)(showWhenPaused: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)) |
| [setStackIndicatorVisible](set-stack-indicator-visible.md) | [main]<br>open suspend fun [setStackIndicatorVisible](set-stack-indicator-visible.md)(visible: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)) |
| [setTopOffsetDp](set-top-offset-dp.md) | [main]<br>open suspend fun [setTopOffsetDp](set-top-offset-dp.md)(offsetDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)) |
| [setWidgetSpacing](set-widget-spacing.md) | [main]<br>open suspend fun [setWidgetSpacing](set-widget-spacing.md)(spacing: [WidgetSpacing](../-widget-spacing/index.md)) |
| [updateEventSettings](update-event-settings.md) | [main]<br>abstract suspend fun [updateEventSettings](update-event-settings.md)(transform: ([EventSettings](../../core.data.prefs/-event-settings/index.md)) -&gt; [EventSettings](../../core.data.prefs/-event-settings/index.md)) |
| [updateHeaderSettings](update-header-settings.md) | [main]<br>abstract suspend fun [updateHeaderSettings](update-header-settings.md)(transform: ([HeaderSettings](../../core.data.prefs/-header-settings/index.md)) -&gt; [HeaderSettings](../../core.data.prefs/-header-settings/index.md)) |
| [updateMediaPlayerSettings](update-media-player-settings.md) | [main]<br>abstract suspend fun [updateMediaPlayerSettings](update-media-player-settings.md)(transform: ([MediaPlayerSettings](../-media-player-settings/index.md)) -&gt; [MediaPlayerSettings](../-media-player-settings/index.md)) |
| [updateWidgetAreaSettings](update-widget-area-settings.md) | [main]<br>abstract suspend fun [updateWidgetAreaSettings](update-widget-area-settings.md)(transform: ([WidgetAreaSettings](../-widget-area-settings/index.md)) -&gt; [WidgetAreaSettings](../-widget-area-settings/index.md)) |
