package core.data.apps

import core.data.apps.model.App

/** Saves the Last-known App list Home resolves before the live App load finishes. */
interface ILastKnownAppStore {
    /** Rewrites the Last-known App list to match [apps]; a Private Space App is never saved. */
    suspend fun save(apps: List<App>)
}
