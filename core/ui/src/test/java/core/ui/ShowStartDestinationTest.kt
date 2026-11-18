package core.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowStartDestinationTest {
    @Test
    fun `the shown start destination needs no navigation`() {
        val inputRoute: String? = "launcher"
        val inputStartDestination: String = "launcher"

        val actualIsStart: Boolean = isStartDestination(route = inputRoute, startDestination = inputStartDestination)

        assertTrue(actualIsStart)
    }

    @Test
    fun `another shown destination is not the start`() {
        val inputRoute: String? = "settings"
        val inputStartDestination: String = "launcher"

        val actualIsStart: Boolean = isStartDestination(route = inputRoute, startDestination = inputStartDestination)

        assertFalse(actualIsStart)
    }

    @Test
    fun `a missing destination is not the start`() {
        val inputRoute: String? = null
        val inputStartDestination: String = "launcher"

        val actualIsStart: Boolean = isStartDestination(route = inputRoute, startDestination = inputStartDestination)

        assertFalse(actualIsStart)
    }
}
