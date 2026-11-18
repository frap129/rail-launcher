# 6. Cache rendered icons on disk

- Status: Accepted
- Date: 2026-11-17
- Ticket: rail-5in.14

## Context

Every `LaunchableIcon` Rail draws loads its icon from the App's resources and
decodes it before the frame that shows it. Home resolves its Favorites from the
Last-known App list on a cold start (ADR 0005), but the icons still load
through `LauncherApps` for each Profile. rail-5in.11's seeded-Favorites
baseline measured Home's icons drawn 1.0-1.5 s after Application creation,
after Home and its Widget Area places had already drawn: the icons are the last
thing Home waits for. The App List, Search and the icon pickers draw the same
way, so the cache serves every surface.

Android's launcher icon API takes no cache key. An App's system icon changes
when its package updates (the APK modification time `IProfileSource` records as
the App's update time) and, without an update, when the system's icon theming,
overlay or locale changes or the App swaps its icon in place. Icon pack, Custom
and Shortcut icons already resolve through Rail's own storage and must keep
their exact appearance.

## Options considered

**A. Coil's disk cache.** Coil is already Rail's image loader and owns the
memory cache that keys icons by `IconKeyer`. Its `DiskCache` is not part of the
fetch pipeline in Coil 3.6.3, though: the engine only passes a disk cache key
through, and no interceptor reads or writes that cache for a `Fetcher`'s
result. A fetcher's `ImageFetchResult` never reaches disk, and a disk entry is
never revalidated before use. Wrapping the rendered bitmaps in the cache would
still need the key, the rasterization and the eviction policy; per-key `remove`
also cannot express "every icon of a package", because each key carries the
App's update time, so a package change would clear every icon instead.

**B. Keep the source data instead of rendered bitmaps.** Adaptive icons are
composed by the framework at draw time; caching the drawable's inputs would
not avoid the composition that the cache exists to remove.

**C. A private file store of rendered PNGs.** One file per icon, request size
and App update time, in Rail's cache directory. A hit is one PNG decode; a miss
renders the drawable at the requested size and stores it. Package changes and
removals delete the package's directory.

## Decision

`IHomeIconCache` stores a PNG per icon and request size in `cacheDir` under the
icon's package directory. Its files live in a versioned directory,
`home-icons-v1`, where the version is one constant: it must be bumped whenever
Rail's rendering changes (the rasterization, or a mask it applies), because
stored bitmaps keep the rendering they were drawn with. At initialisation the
cache deletes every sibling `home-icons*` directory of an earlier version, off
the main thread.

Keys cover what changes an icon's pixels:

- The icon's identity, `IconSource.identity`: the icon variant, including its
  Profile badge and, for a pack icon, the pack that maps it.
- The App's update time, carried on `Icon.ActivityIcon`. `IconKeyer` includes it
  in Coil's memory key too, so an App update withdraws both the in-memory and
  the stored bitmap.
- Density and request size: a bitmap rendered for one slot is not drawn in
  another.

Only an App's system Activity icon takes part. Icon pack, Custom and Shortcut
icons keep their current path, and an icon without a request size or a known
update time is never stored.

A key hit returns the stored bitmap without loading the App's resources. At most
once per key between configuration changes that can alter system icons, the
fetcher then launches a background revalidation on the application scope, on
the IO dispatcher: it loads the system drawable, renders it with the same code
as the miss path (fitted and centred, so a non-square icon keeps its aspect
ratio), and compares it with the stored bitmap after the same PNG round trip
(`Bitmap.sameAs`), so premultiplied-alpha rounding never counts as a change.
Only a different bitmap is stored, through the same atomic, per-key-serialised
write that publishes a completed file with a rename. An unchanged system icon
writes nothing. This is what replaces an icon whose system theming, overlay or
locale changed without an App update, and an App update that changed the update
time is a new key instead.

A launcher process outlives night mode, locale and overlay changes, so the
cache listens for configuration changes on the application context. A change to
the UI mode, the locale or the system's asset paths (overlays, wallpaper
colours) releases every revalidation claim and bumps the cache's configuration
generation, which `IconKeyer` adds to Coil's memory key. The recreated activity's
icons then miss Coil's memory cache, read the stored bitmap and revalidate it
again. Rotation and other layout-only changes do neither.

A replacement bumps the icon identity's revalidation generation, which lives on
the cache as one `StateFlow` keyed by `IconSource.identity`. Two readers share
it, so both reload the replaced icon and nothing else:

- `IconKeyer` includes it in Coil's memory key, so the re-request cannot be
  answered by the stale memory entry.
- Every drawn `LaunchableIcon` observes its own identity's entry through
  `LocalHomeIconCache`, and restarts its Coil painter when that entry changes.
  The request is pinned to the icon slot's pixel size, so a restart re-executes
  with a new memory key, reads the replaced bitmap and draws it in place,
  without scrolling or recreating the icon. An icon whose entry did not change
  does not recompose or reload.

Rail has no icon theme or shape setting: every icon is the system drawable, and
an adaptive icon's mask comes with it, so there is no theme or shape token to
key on. When such a setting lands, its value must join the key, and the render
version bounds the stored bitmaps of the old setting.

`PackageChanged`, `PackageRemoved` and `PackagesUnavailable` changes evict the
package's icons through the existing `IProfileSource.changes` callback, rather
than another receiver. `PackageAdded` cannot have stored icons, and an App
update arrives as a `PackageChanged` or `PackagesUnavailable` change. After the
live App list first loads, the cache deletes the package directories the live
list does not have, so a package removed while Rail was dead is reclaimed too.

A Private Space icon is never written. The cache stores only an icon whose
Profile is known and not private, and an unbadged icon only when the personal
Profile lists its App, so a Private Space's or an unknown Profile's icon cannot
reach the disk even if it arrives unbadged.

A miss renders the bitmap, returns it, and stores it on the application scope
after the draw, so the draw never waits for the write.

`cacheDir` is outside Android cloud backup and device transfer: the platform
excludes an app's cache directories from both, so the existing backup and data
extraction rules need no entry and ADR 0004's and ADR 0005's scopes do not grow.
The cache is derived data, so the system may clear it at will.

## Consequences

- Every surface draws a stored icon without loading its App's resources; a
  cleared or missing cache falls back to loading each icon and stores it again.
- An App update, a package change or removal, a different request size or a
  changed system icon never draws a stale icon: the key changes, the revalidation
  replaces the stored bitmap, or the package's directory goes.
- A revalidated icon's drawn copies reload and show the new bitmap in place,
  only for the icon whose stored bitmap changed; Coil's stale memory entry is
  bypassed by the same generation.
- A Private Space icon never reaches the disk, including while the Space is
  Unlocked.
- The cache holds at most one file per App icon, density and size, so its size
  grows with the icons Rail draws, not with App updates, and the version and
  orphan sweeps reclaim what no icon needs.
- Icon pack, Custom and Shortcut icons gain nothing from the cache; caching pack
  icons would need the pack's own update time in the key.
