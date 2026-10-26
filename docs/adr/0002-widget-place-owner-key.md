# 2. Name the Place owner column owner_key

- Status: Accepted
- Date: 2026-10-26
- Ticket: rail-6es.20

## Context

The Pop-ups epic (rail-6es) planned for `widget_places` to gain a `popup_key`
column: null for a Place in the Widget Area, otherwise the key of the Pop-up
that owns the Place. The shipped `WidgetPlaceRecord` and schema use `owner_key`
instead. The behaviour is already correct; this record aligns the decision with
the shipped name so the next implementer does not add a second column or rename
the existing one.

## Decision

`widget_places.owner_key` is the Pop-up Owner key. `NULL` marks a Place in the
Widget Area; any other value is the key of the Launchable whose Pop-up owns the
Place. It reuses the owner-key vocabulary of `popup_items.owner_key` and
`popup_hidden_shortcuts.owner_key`; unlike those tables, which always point at a
Pop-up Owner, a Place may also belong to the Widget Area.

The epic's `popup_key` name is superseded; do not add it.

## Consequences

- The Widget Area is the rows with `owner_key IS NULL`; a Pop-up's Place is the
  row with `owner_key = :ownerKey`.
- New schema and queries use `owner_key`, matching the other Pop-up tables.
- No migration or code change follows from this record.
