package core.ui.model.data

/** Route of the Home (launcher) destination. */
const val LAUNCHER_ROUTE: String = "launcher"

/** Route root of the Settings screens. */
const val SETTINGS_ROUTE: String = "settings"

/** The Widgets settings screen. */
const val WIDGETS_SETTINGS_ROUTE: String = "$SETTINGS_ROUTE/widgets"

/** The Widgets settings screen scrolled to the Media Player section. */
const val WIDGETS_MEDIA_SETTINGS_ROUTE: String = "$SETTINGS_ROUTE/widgets?section=media"

/** The Pop-ups and Folders screen key under [SETTINGS_ROUTE]. */
const val POPUPS_SETTINGS_KEY: String = "popups"

/** The Pop-ups and Folders settings screen. */
const val POPUPS_SETTINGS_ROUTE: String = "$SETTINGS_ROUTE/$POPUPS_SETTINGS_KEY"
