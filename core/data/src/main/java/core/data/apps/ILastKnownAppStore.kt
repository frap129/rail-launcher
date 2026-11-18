package core.data.apps

import core.data.apps.model.App

/** Stores the Last-known App list Home resolves before the live App load finishes, and reads it back. */
interface ILastKnownAppStore {
    /** Rewrites the Last-known App list to match [apps]; a Private Space App is never stored. */
    suspend fun save(apps: List<App>)

    /** Every stored entry of the Last-known App list, or an empty list when none was stored. */
    suspend fun read(): List<LastKnownAppRecord>
}
