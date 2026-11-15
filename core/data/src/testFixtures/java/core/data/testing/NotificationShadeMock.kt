package core.data.testing

import core.data.notifications.INotificationShade
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk

/** A MockK [INotificationShade] whose expand succeeds. */
fun createNotificationShadeMock(): INotificationShade {
    val mockNotificationShade: INotificationShade = mockk()
    every { mockNotificationShade.expand() } just Runs
    return mockNotificationShade
}
