# Roadmap

Order for the epics in beads. Simple, quick wins that help
existing users come first; blocking dependencies are respected. Sizes are
rough estimates.

## Phase 1: quick wins for current users

| # | Epic | Size | Notes |
|---|---|---|---|
| 1 | ~~App List additions (`rail-pog`)~~ | S–M | **Done.** Recently installed, haptics toggle, hide the Rail, install into Private Space. Language-aware alphabet last. |
| 2 | ~~Suggested apps on Home (`rail-ccs`)~~ | S–M | **Done.** Pure projection over existing launch data. |
| 3 | Agenda sheet and weekday clock (`rail-dol`) | S–M | Reuses the calendar source and Header tap targets. Unblocks Keyboard and Integrations. |
| 4 | Keyboard and input (`rail-49n`) | S | Extends `LauncherKeyHandler`. |
| 5 | Default launcher and first run (`rail-746`) | S–M | Advanced rows and rotation first, then prompt and onboarding. Unblocks Large screens and Integrations. |

## Phase 2: the biggest gaps

| # | Epic | Size | Notes |
|---|---|---|---|
| 6 | Notifications on Home (`rail-vae`) | M | Reuses the notification source, actions and inline reply. |
| 7 | Appearance settings (`rail-v4e`) | M–L | Unblocks Themes and Large screens. |
| 8 | Backup and restore (`rail-quf`) | M–L | Versioned format picks up themes later. |
| 9 | Double-tap to sleep (`rail-t0v`) | S–M | Start the Play accessibility declaration early. |

## Phase 3: customization depth

| # | Epic | Size | Notes |
|---|---|---|---|
| 10 | Icon styles (`rail-e0l`) | M | Shapes, Dots, per-pack memory and suggestions first; Rail icon artwork when ready. |
| 11 | Themes (`rail-krg`) | L | Needs Appearance settings. Ready-made themes need design work. |
| 12 | Floating Home button (`rail-e9x`) | M | |
| 13 | Wallpaper (`rail-bhv`) | M | Unblocks `/screenshot`. |
| 14 | Notification Summary (`rail-duj`) | M–L | |

## Phase 4: niche or broad

| # | Epic | Size | Notes |
|---|---|---|---|
| 15 | Integrations and commands (`rail-1pe`) | S–M | Waits on `rail-dol`, `rail-bhv` and `rail-746`. |
| 16 | Wellbeing and context (`rail-4sa`) | L | Three separate features. |
| 17 | Large screens (`rail-avp`) | L | Tablets and foldables only. |

## Parallel

- Translations (`rail-dj7`): human work, any time.
