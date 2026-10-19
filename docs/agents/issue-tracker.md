# Issue tracker: beads

Issues and specs for this repo live in beads (`bd` CLI). IDs look like `rail-<hash>`. Data syncs through the Dolt remote on `origin`; `.beads/` config is committed.

## Conventions

- Create: `bd create "<title>" -t <bug|feature|task|epic|chore|spike|decision> -p <0-4> --body-file <file>` (or `-d "<body>"`)
- A spec is an `epic`; implementation tickets are children: `--parent <epic-id>`, one ticket per issue
- Triage state is a label (see `triage-labels.md`): `bd update <id> --add-label <role> --remove-label <old-role>`
- Conversation history: `bd comment <id> "<text>"`
- Done: `bd close <id> --reason "<summary>"`; won't do: add `wontfix` label, then close

## When a skill says "publish to the issue tracker"

Run `bd create` with the body written to a temp file and passed via `--body-file`. Report the returned ID.

## When a skill says "fetch the relevant ticket"

`bd show <id>` (add `--json` for structured output). Use `bd list`, `bd search "<text>"`, or `bd children <id>` to find it if no ID is given.

## Wayfinding operations

Used by `/wayfinder`. The **map** is an epic with one **child** issue per ticket.

- **Map**: an `epic` whose description holds the Notes / Decisions-so-far / Fog body. Edit with `bd update <map-id> --body-file <file>`.
- **Child ticket**: `bd create "<question>" --parent <map-id> -t task -l <research|prototype|grilling|task>`, with the question in the body. The label records the ticket type.
- **Blocking**: `bd dep add <blocked-id> <blocker-id>` (or `--deps blocked-by:<id>` at create time). A ticket is unblocked when all of its blockers are closed.
- **Frontier**: `bd ready --parent <map-id> --unassigned` lists open, unblocked, unclaimed children; take the oldest/highest priority.
- **Claim**: `bd update <id> --claim` before any work.
- **Resolve**: `bd comment <id> "## Answer ..."`, `bd close <id>`, then append a context pointer (gist + ID) to Decisions-so-far in the map epic's description.
