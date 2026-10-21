package feature.widgets

import core.data.testing.FakeCalendarEventSource
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarPermissionGateTest {
    @Test
    fun `enables when permission is already granted`() {
        val calendarEventSource: FakeCalendarEventSource = FakeCalendarEventSource(hasPermission = true)
        val gate: CalendarPermissionGate = CalendarPermissionGate(calendarEventSource)

        val actual: CalendarPermissionDecision = gate.decideEnable()

        assertEquals(CalendarPermissionDecision.Enable, actual)
    }

    @Test
    fun `requests permission when permission is missing`() {
        val calendarEventSource: FakeCalendarEventSource = FakeCalendarEventSource(hasPermission = false)
        val gate: CalendarPermissionGate = CalendarPermissionGate(calendarEventSource)

        val actual: CalendarPermissionDecision = gate.decideEnable()

        assertEquals(CalendarPermissionDecision.Request, actual)
    }

    @Test
    fun `returns enable and refreshes permission after a grant`() {
        val calendarEventSource: FakeCalendarEventSource = FakeCalendarEventSource(hasPermission = false)
        val gate: CalendarPermissionGate = CalendarPermissionGate(calendarEventSource)

        val actual: CalendarPermissionDecision = gate.onPermissionResult(granted = true, canAskAgain = true)

        assertEquals(CalendarPermissionDecision.Enable, actual)
        assertEquals(1, calendarEventSource.refreshCount)
    }

    @Test
    fun `returns temporary denial after a denial that can be asked again`() {
        val calendarEventSource: FakeCalendarEventSource = FakeCalendarEventSource(hasPermission = false)
        val gate: CalendarPermissionGate = CalendarPermissionGate(calendarEventSource)

        val actual: CalendarPermissionDecision = gate.onPermissionResult(granted = false, canAskAgain = true)

        assertEquals(CalendarPermissionDecision.Denied(permanentlyDenied = false), actual)
    }

    @Test
    fun `remembers permanent denial for subsequent enable attempts`() {
        val calendarEventSource: FakeCalendarEventSource = FakeCalendarEventSource(hasPermission = false)
        val gate: CalendarPermissionGate = CalendarPermissionGate(calendarEventSource)
        gate.onPermissionResult(granted = false, canAskAgain = false)

        val actual: CalendarPermissionDecision = gate.decideEnable()

        assertEquals(CalendarPermissionDecision.Denied(permanentlyDenied = true), actual)
    }
}
