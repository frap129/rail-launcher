# 4. Back up the whole database, private rows included

- Status: Accepted
- Date: 2026-10-29
- Ticket: rail-25f.12
- Supersedes: [3. Exclude Rail's database from Android backup](0003-exclude-database-from-backup.md)

## Context

ADR 0003 excluded the whole `database` domain because backup rules work per
file while every Rail preference shares one Room database, so private-profile
rows could not be separated from personal and work rows. The owner has since
decided that Rail data must be backed up, private-profile rows included. This
record supersedes ADR 0003 and the epic rule that private app keys never leave
the device through Android backup.

Restored keys embed the `UserHandle{userId}` Profile token. Android can assign
different user ids on another device, or the work profile or Private Space may
be absent, so a restored key can point at a Profile Rail no longer sees.
Personal Profile keys use user 0 and restore intact.

## Decision

Keep all Rail state in `AppDatabase` and include the whole database in Android
cloud backup and device transfer. `app/src/main/res/xml/backup_rules.xml` and
`app/src/main/res/xml/data_extraction_rules.xml` contain no database exclude;
the default rules include every file in the database directory, including the
SQLite `-wal` and `-shm` sidecars.

No checkpoint or custom `BackupAgent` is needed. Auto Backup stops the app
before it copies app data, and the default file backup includes the database
file and its sidecars as one set, so SQLite opens a consistent database after
the set is restored. Excluding the sidecars or copying only `app-database`
could lose committed rows that are still in the WAL; keeping the directory
default avoids that.

Restored keys follow the existing resolution and stale-key cleanup:

- A key whose Profile token matches a current Profile and whose Launchable or
  Shortcut is still available resolves normally. Personal-profile data always
  takes this path.
- A key whose Profile is missing, or whose Launchable is no longer installed,
  is removed by the existing stale-key cleanups for Favorites, name
  customizations, Header tap actions, Pop-up items and owners, hidden
  Shortcuts, Folder members and Widget Places.
- A key for a Private Space that is Locked on the restored device is kept, not
  shown, while the Space is Locked, and resolves when it Unlocks. The kept-key
  behaviour from rail-25f.4 and rail-25f.7 is unchanged.
- A key for a missing Profile never crashes resolution: keys are parsed as
  text, and a token with no matching Profile resolves to nothing until cleanup
  removes the row.

## Consequences

- Android cloud backup and device transfer now include private-profile
  Favorites, settings, Folders, Pop-ups and their keys, deliberately overriding
  the privacy rule of ADR 0003.
- Restoring on the same device preserves personal, work and private data.
- Restoring on a device with different Profile ids drops keys that no longer
  match, while personal data survives and current work and private data
  resolves.
- Rail still has no backup/restore feature of its own; it relies on Android.
