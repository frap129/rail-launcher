package core.ui.composables

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.mockk.every
import io.mockk.mockk

/** A mocked [LifecycleOwner] whose [registry] drives its lifecycle during a test. */
data class LifecycleFixture(val owner: LifecycleOwner, val registry: LifecycleRegistry)

/** Builds a mocked [LifecycleOwner] at [state]. */
fun createLifecycleFixture(state: Lifecycle.State): LifecycleFixture {
    val mockLifecycleOwner: LifecycleOwner = mockk()
    val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(mockLifecycleOwner)
    every { mockLifecycleOwner.lifecycle } returns lifecycleRegistry
    lifecycleRegistry.currentState = state
    return LifecycleFixture(owner = mockLifecycleOwner, registry = lifecycleRegistry)
}
