package core.data.launchables

import core.data.apps.model.App

/** Rewrites Legacy App keys stored in the database to their package representative's current key. */
interface ILegacyAppKeyUpgrader {
    /** Rewrites every stored Legacy App key that matches one of [apps]. */
    suspend fun upgrade(apps: List<App>)
}
