# 5. Exclude the Last-known App list from Android backup

- Status: Accepted
- Date: 2026-11-17
- Ticket: rail-5in.12

## Context

Epic rail-5in makes Home usable before the live `LauncherApps` load finishes.
Rail therefore saves the installed App list after every live load as the
Last-known App list, and Home resolves its Favorites from it on a cold start.
The list holds each App's component, Profile serial, label, badged label, flags,
install time and update time. The update time is the installed APK's
modification time (`File(applicationInfo.sourceDir).lastModified()`), the same
value rail-5in.14 keys its icon bitmaps on.

ADR 0004 deliberately includes the whole `AppDatabase` in Android cloud backup
and device transfer, private-profile rows included, because backups work per
file and private rows cannot be separated from other rows. The Last-known App
list does not belong in that scope: it is derived, rebuildable data
reconstructed from the device's current Apps, and restoring it on another
device could show Apps the device no longer has or reveal the presence of
Private Space Apps.

## Options considered

**A table in `AppDatabase`.** Adding the Last-known App list to the backed-up
database would either put rebuildable data into the backup and device transfer
ADR 0004 covers, or require row-level filtering that Android backup does not
support. It would also make a stale Last-known App list survive a restore next
to the live data that replaces it.

**A file in `filesDir`.** A plain file needs a hand-written format and an
atomic write, and Rail has no serialization library. Room already provides
atomic replacement, typed rows and a tested schema workflow.

**A separate Room database, excluded from backup.** This keeps the Last-known
App list outside `AppDatabase` and lets one rule per database file exclude it,
while reusing Room as the only storage mechanism Rail already uses.

## Decision

Store the Last-known App list in a separate Room database,
`LastKnownAppDatabase`, in the `last-known-apps` file. Its single
`last_known_apps` table holds `LastKnownAppRecord` rows keyed by component and
Profile serial. `IAppRepositoryImpl` saves the whole list after every live App
load and live change, in a job it owns on the application scope, so saving never
blocks the main thread or delays the live App publish. `ILastKnownAppStoreImpl`
never writes a Private Space App, whether the Space is Locked or Unlocked.

`LastKnownAppDatabase` is built with destructive fallback, so a schema change
drops and rebuilds the whole Last-known App list.

`app/src/main/res/xml/backup_rules.xml` and
`app/src/main/res/xml/data_extraction_rules.xml` exclude
`LastKnownAppDatabase.DATABASE_NAME` and all of its sidecars (`-journal`,
`-wal` and `-shm`) from backup and device transfer.

ADR 0004 still covers Rail state in `AppDatabase`. This record adds a scope
note to ADR 0004 because the Last-known App list is derived, rebuildable data
rather than Rail state, so ADR 0004's backup scope does not grow.

## Consequences

- Android backup and device transfer never carry the Last-known App list, so a
  restored device rebuilds it from its own live App load.
- Private Space Apps are never part of the Last-known App list, so its files
  cannot reveal them.
- Losing the Last-known App list costs one live App load; Home falls back to the
  current behaviour until the list is rebuilt.
- A schema change to the Last-known App list database drops and rebuilds it,
  because no user-authored data lives there.
- Future key-bearing Rail tables still belong in `AppDatabase` and inherit ADR
  0004's backup scope; a new rebuildable store must get its own excluded
  database and an ADR record like this one.
