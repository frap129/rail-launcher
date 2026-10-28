package core.data.testing

import core.data.media.IMediaSessionSource
import core.data.media.MediaSessionSnapshot
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow

/** A MockK [IMediaSessionSource] over [sessions] and [hasAccess]. */
fun createMediaSessionSourceMock(sessions: Flow<List<MediaSessionSnapshot>> = emptyFlow(), hasAccess: Boolean = true): IMediaSessionSource {
    val mockMediaSessionSource: IMediaSessionSource = mockk()
    every { mockMediaSessionSource.hasAccess } returns MutableStateFlow(hasAccess)
    every { mockMediaSessionSource.sessions } returns sessions
    every { mockMediaSessionSource.execute(any(), any()) } just Runs
    every { mockMediaSessionSource.open(any()) } returns true
    return mockMediaSessionSource
}
