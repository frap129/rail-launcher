package core.ui.model.data

/** Route of the Home (launcher) destination. */
const val LAUNCHER_ROUTE: String = "launcher"

/** Route of the first-run and replayable onboarding flow. */
const val ONBOARDING_ROUTE: String = "onboarding"

/** Route root of the Settings screens. */
const val SETTINGS_ROUTE: String = "settings"

/** The Rail Settings main screen key under [SETTINGS_ROUTE]. */
const val SETTINGS_MAIN_KEY: String = "main"

/** The Rail Settings main screen. */
const val SETTINGS_MAIN_ROUTE: String = "$SETTINGS_ROUTE/$SETTINGS_MAIN_KEY"

/** The Widgets settings screen. */
const val WIDGETS_SETTINGS_ROUTE: String = "$SETTINGS_ROUTE/widgets"

/** The Widgets settings screen scrolled to the Media Player section. */
const val WIDGETS_MEDIA_SETTINGS_ROUTE: String = "$SETTINGS_ROUTE/widgets?section=media"

/** The Search screen key under [SETTINGS_ROUTE]. */
const val SEARCH_SETTINGS_KEY: String = "search"

/** The Search settings screen. */
const val SEARCH_SETTINGS_ROUTE: String = "$SETTINGS_ROUTE/$SEARCH_SETTINGS_KEY"

/** The Gestures and Pop-ups screen key under [SETTINGS_ROUTE]. */
const val POPUPS_SETTINGS_KEY: String = "popups"

/** The Gestures and Pop-ups settings screen. */
const val POPUPS_SETTINGS_ROUTE: String = "$SETTINGS_ROUTE/$POPUPS_SETTINGS_KEY"
