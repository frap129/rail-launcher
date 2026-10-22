package core.data.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcludedPackagesConvertersTest {
    private val converters: ExcludedPackagesConverters = ExcludedPackagesConverters()

    @Test
    fun `round trips excluded packages`() {
        val packages: Set<String> = setOf("com.example.video", "com.example.navigation")
        assertEquals(packages, converters.fromStringToExcludedPackages(converters.fromExcludedPackagesToString(packages)))
    }

    @Test
    fun `decodes an empty string as an empty set`() {
        assertTrue(converters.fromStringToExcludedPackages("").isEmpty())
    }
}
