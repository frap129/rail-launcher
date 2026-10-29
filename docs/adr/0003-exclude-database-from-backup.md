# 3. Exclude Rail's database from Android backup

- Status: Superseded by [4. Back up the whole database, private rows included](0004-back-up-whole-database.md)
- Date: 2026-10-28
- Ticket: rail-25f.4

## Context

Rows whose key belongs to a Private Space Profile (Favorites, name
customizations, Header tap actions, Pop-up items, hidden Shortcuts, Folder
members and the App Widget Places they own) must never reach cloud backup or
device transfer. Backup rules work per file, while every Rail preference lives
in one Room database file, so a rule cannot separate private rows from
non-private ones. The ticket asked for a separate database for private-profile
rows or a stripping backup agent, and allowed excluding the whole database only
if both are unworkable.

All persisted Rail state is in `AppDatabase`: there is no `SharedPreferences`,
DataStore or file store. Rail has no restore feature, and the Pop-ups epic
already treats a lost database as a lost set of preferences.

## Options considered

**Separate database for private-profile rows.** Every key-bearing table would
need a second DAO and a write path that routes each row by Profile kind:
`favorite_records`, `launchable_customizations`, `header_settings` (two columns
on one row), `popup_items`, `popup_hidden_shortcuts`, `folders` membership and
`widget_places`. Several writes mix Profiles in one call and rely on the single
database's transactions: Folder create/delete spans the Folder, its items, its
hidden Shortcuts, its Places and its Favorite; a Pop-up edit rewrites items and
pins; Edit Favorites reorders a list that can mix Profiles. Room cannot span two
database files in one transaction, so these flows would gain partial-failure
states the code currently avoids. Stale-key cleanup would also need a
Profile-kind lookup for every key, and a single missed table silently leaks.
Migrating existing rows cannot classify their keys without a running
`IProfileSource`.

**A stripping backup agent.** `BackupAgent` has no row-level hook. An agent
would have to copy the live database, open the copy, resolve the private
Profile tokens in a backup context, delete the matching rows across every table
(including keys embedded in `header_settings` columns), then write the copy to
`FullBackupDataOutput`. The code cannot run in the JVM test suite, would have to
be repeated for each backup transport, and any exception or newly added
key-bearing table would leak private keys or corrupt the backup silently.

## Decision

Exclude the whole database domain from Android backup and device transfer:

- `app/src/main/res/xml/backup_rules.xml` excludes `domain="database"`.
- `app/src/main/res/xml/data_extraction_rules.xml` excludes
  `domain="database"` for both `cloud-backup` and `device-transfer`.

Rail never writes Private Space data outside that database.

## Consequences

- No private-profile key can leave the device through backup or transfer.
- Non-private Rail data (Favorites, settings, Folders, Widgets) also stops
  backing up. After a device restore those preferences are recreated by the
  user; this is acceptable because Rail stores no user-authored content and has
  no restore feature of its own.
- A future Rail backup/restore or export feature must first move private-profile
  rows to a separately excluded store and revisit this record.
- Any new key-bearing table inherits the same protection because the rule
  covers the whole file, so forgetting a table cannot leak.
