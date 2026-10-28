package core.data.calendar

import core.data.testing.createCalendarEventSourceMock
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class ICalendarPermissionGateImplTest {
    @Test
    fun `enables when permission is already granted`() {
        val permission: MutableStateFlow<Boolean> = MutableStateFlow(true)
        val mockCalendarEventSource: ICalendarEventSource = createCalendarEventSourceMock(permission = permission)
        val gate: ICalendarPermissionGate = ICalendarPermissionGateImpl(mockCalendarEventSource)

        val actualDecision: CalendarPermissionDecision = gate.decideEnable()

        assertEquals(CalendarPermissionDecision.Enable, actualDecision)
    }

    @Test
    fun `requests permission when permission is missing`() {
        val mockCalendarEventSource: ICalendarEventSource = createCalendarEventSourceMock()
        val gate: ICalendarPermissionGate = ICalendarPermissionGateImpl(mockCalendarEventSource)

        val actualDecision: CalendarPermissionDecision = gate.decideEnable()

        assertEquals(CalendarPermissionDecision.Request, actualDecision)
    }

    @Test
    fun `returns enable and refreshes permission after a grant`() {
        val mockCalendarEventSource: ICalendarEventSource = createCalendarEventSourceMock()
        val gate: ICalendarPermissionGate = ICalendarPermissionGateImpl(mockCalendarEventSource)

        val actualDecision: CalendarPermissionDecision = gate.onPermissionResult(granted = true, canAskAgain = true)

        assertEquals(CalendarPermissionDecision.Enable, actualDecision)
        verify(exactly = 1) { mockCalendarEventSource.refreshPermission() }
    }

    @Test
    fun `returns temporary denial after a denial that can be asked again`() {
        val mockCalendarEventSource: ICalendarEventSource = createCalendarEventSourceMock()
        val gate: ICalendarPermissionGate = ICalendarPermissionGateImpl(mockCalendarEventSource)

        val actualDecision: CalendarPermissionDecision = gate.onPermissionResult(granted = false, canAskAgain = true)

        assertEquals(CalendarPermissionDecision.Denied(permanentlyDenied = false), actualDecision)
    }

    @Test
    fun `remembers permanent denial for subsequent enable attempts`() {
        val mockCalendarEventSource: ICalendarEventSource = createCalendarEventSourceMock()
        val gate: ICalendarPermissionGate = ICalendarPermissionGateImpl(mockCalendarEventSource)
        gate.onPermissionResult(granted = false, canAskAgain = false)

        val actualDecision: CalendarPermissionDecision = gate.decideEnable()

        assertEquals(CalendarPermissionDecision.Denied(permanentlyDenied = true), actualDecision)
    }

    @Test
    fun `clears a permanent denial once permission is held again`() {
        val permission: MutableStateFlow<Boolean> = MutableStateFlow(false)
        val mockCalendarEventSource: ICalendarEventSource = createCalendarEventSourceMock(permission = permission)
        val gate: ICalendarPermissionGate = ICalendarPermissionGateImpl(mockCalendarEventSource)
        gate.onPermissionResult(granted = false, canAskAgain = false)
        permission.value = true
        gate.refreshPermission()
        permission.value = false

        val actualDecision: CalendarPermissionDecision = gate.decideEnable()

        assertEquals(CalendarPermissionDecision.Request, actualDecision)
    }
}
