package core.data.shortcuts

import core.data.apps.model.AppProfile

/**
 * Owns every system Shortcut pin, merging the Pop-up Shortcut items and every [IShortcutPinSource]
 * into one set per package and Profile.
 */
interface IShortcutPinCoordinator {
    /** Reapplies the merged pin sets for [affectedProfiles], after the caller's transaction commits if it is in one. */
    suspend fun reapplyPins(affectedProfiles: Set<AppProfile>)
}
