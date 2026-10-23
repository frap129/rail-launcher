package core.data.calendar

import core.data.testing.FakeCalendarEventSource
import org.junit.Assert.assertEquals
import org.junit.Test

class ICalendarPermissionGateImplTest {
    @Test
    fun `enables when permission is already granted`() {
        val mockCalendarEventSource: FakeCalendarEventSource = FakeCalendarEventSource(hasPermission = true)
        val gate: ICalendarPermissionGate = ICalendarPermissionGateImpl(mockCalendarEventSource)

        val actual: CalendarPermissionDecision = gate.decideEnable()

        assertEquals(CalendarPermissionDecision.Enable, actual)
    }

    @Test
    fun `requests permission when permission is missing`() {
        val mockCalendarEventSource: FakeCalendarEventSource = FakeCalendarEventSource(hasPermission = false)
        val gate: ICalendarPermissionGate = ICalendarPermissionGateImpl(mockCalendarEventSource)

        val actual: CalendarPermissionDecision = gate.decideEnable()

        assertEquals(CalendarPermissionDecision.Request, actual)
    }

    @Test
    fun `returns enable and refreshes permission after a grant`() {
        val mockCalendarEventSource: FakeCalendarEventSource = FakeCalendarEventSource(hasPermission = false)
        val gate: ICalendarPermissionGate = ICalendarPermissionGateImpl(mockCalendarEventSource)

        val actual: CalendarPermissionDecision = gate.onPermissionResult(granted = true, canAskAgain = true)

        assertEquals(CalendarPermissionDecision.Enable, actual)
        assertEquals(1, mockCalendarEventSource.refreshCount)
    }

    @Test
    fun `returns temporary denial after a denial that can be asked again`() {
        val mockCalendarEventSource: FakeCalendarEventSource = FakeCalendarEventSource(hasPermission = false)
        val gate: ICalendarPermissionGate = ICalendarPermissionGateImpl(mockCalendarEventSource)

        val actual: CalendarPermissionDecision = gate.onPermissionResult(granted = false, canAskAgain = true)

        assertEquals(CalendarPermissionDecision.Denied(permanentlyDenied = false), actual)
    }

    @Test
    fun `remembers permanent denial for subsequent enable attempts`() {
        val mockCalendarEventSource: FakeCalendarEventSource = FakeCalendarEventSource(hasPermission = false)
        val gate: ICalendarPermissionGate = ICalendarPermissionGateImpl(mockCalendarEventSource)
        gate.onPermissionResult(granted = false, canAskAgain = false)

        val actual: CalendarPermissionDecision = gate.decideEnable()

        assertEquals(CalendarPermissionDecision.Denied(permanentlyDenied = true), actual)
    }

    @Test
    fun `clears a permanent denial once permission is held again`() {
        val mockCalendarEventSource: FakeCalendarEventSource = FakeCalendarEventSource(hasPermission = false)
        val gate: ICalendarPermissionGate = ICalendarPermissionGateImpl(mockCalendarEventSource)
        gate.onPermissionResult(granted = false, canAskAgain = false)
        mockCalendarEventSource.setPermission(granted = true)
        gate.refreshPermission()
        mockCalendarEventSource.setPermission(granted = false)

        val actual: CalendarPermissionDecision = gate.decideEnable()

        assertEquals(CalendarPermissionDecision.Request, actual)
    }
}
