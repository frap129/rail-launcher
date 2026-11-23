package feature.launcher.agenda

/** One-shot requests the agenda sheet sends to its host. */
sealed interface AgendaEvent {
    /** The user permanently denied calendar access, so the host shows the calendar permission dialog. */
    data object ShowCalendarPermissionDenied : AgendaEvent
}
