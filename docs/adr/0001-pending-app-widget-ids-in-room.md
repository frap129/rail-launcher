# 1. Store pending App Widget add-flow ids in Room

- Status: Accepted
- Date: 2026-10-23
- Ticket: rail-e6n.32

## Context

An App Widget add flow allocates a system id, binds it, and may hand control to
the provider's configuration activity. If the process is recreated before the
flow finishes, startup reconciliation must not treat that id as orphaned and
free it. The ids were previously kept in a dedicated `SharedPreferences` file,
which was the only preference storage outside Room.

The rest of the add-flow state (provider, profile, placement and phase) lives
in the `SavedStateHandle` because it restores a single ViewModel instance and
is not a user preference.

## Decision

Store the pending ids in a Room table, `pending_app_widgets`, through
`IAddFlowPendingIdsImpl` and the `PendingAppWidgetDao`. Startup reconciliation
reads the ids from the same database before it runs. The `SavedStateHandle`
continues to hold the transient add-flow details.

## Consequences

- All persisted Rail state is Room-backed, as `AGENTS.md` requires.
- Pending ids survive process death without a second storage mechanism.
- Reading the ids is asynchronous, so reconciliation and restore already run in
  coroutines.
- Losing the Database (for example an uninstall) also loses the protection
  window, the same as losing the previous preferences file.
