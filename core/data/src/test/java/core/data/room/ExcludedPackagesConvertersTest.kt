package core.data.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcludedPackagesConvertersTest {
    private val converters: ExcludedPackagesConverters = ExcludedPackagesConverters()

    @Test
    fun `round trips excluded packages`() {
        val inputPackages: Set<String> = setOf("com.example.video", "com.example.navigation")
        assertEquals(inputPackages, converters.fromStringToExcludedPackages(converters.fromExcludedPackagesToString(inputPackages)))
    }

    @Test
    fun `decodes an empty string as an empty set`() {
        assertTrue(converters.fromStringToExcludedPackages("").isEmpty())
    }
}
