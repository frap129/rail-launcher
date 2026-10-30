package core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class LogTypeNameTest {
    @Test
    fun `returns the exception type name`() {
        val actualTypeName: String = IllegalStateException("private package").logTypeName()

        assertEquals("IllegalStateException", actualTypeName)
    }
}
