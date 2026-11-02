package core.data.shortcuts

import core.data.apps.model.AppProfile

/** Owns every system Shortcut pin, merging every [IShortcutPinSource] into one set per package and Profile. */
interface IShortcutPinCoordinator {
    /** Reapplies the merged pin sets for [affectedProfiles], after the caller's transaction commits if it is in one. */
    suspend fun reapplyPins(affectedProfiles: Set<AppProfile>)

    /**
     * Runs [block] under the pin lock, then reapplies the merged pin sets for [affectedProfiles] after
     * the lock is released and the caller's transaction commits if it is in one, even when the
     * caller is cancelled. The lock keeps every other pin write from running while [block] changes
     * the pin sets. [block] must not call [reapplyPins], because the lock is not reentrant.
     */
    suspend fun <T> runUnderPinLock(affectedProfiles: Set<AppProfile>, block: suspend () -> T): T
}
