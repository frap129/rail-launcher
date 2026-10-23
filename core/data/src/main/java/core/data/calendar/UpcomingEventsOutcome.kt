package core.data.calendar

/** The UI-facing result of an Upcoming events change or permission result. */
sealed interface UpcomingEventsOutcome {
    /** The change was applied, so the UI has nothing to show. */
    data object NoAction : UpcomingEventsOutcome

    data object RequestPermission : UpcomingEventsOutcome

    data class PermissionDenied(val permanentlyDenied: Boolean) : UpcomingEventsOutcome
}
