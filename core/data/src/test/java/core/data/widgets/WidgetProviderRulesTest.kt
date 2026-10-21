package core.data.widgets

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetProviderRulesTest {
    @Test
    fun `own package providers are excluded`() {
        assertFalse(isPickerProvider(isOwnPackage = true, isHomeScreenCategory = true))
    }

    @Test
    fun `providers without the home screen category are excluded`() {
        assertFalse(isPickerProvider(isOwnPackage = false, isHomeScreenCategory = false))
    }

    @Test
    fun `home screen providers from other apps are offered`() {
        assertTrue(isPickerProvider(isOwnPackage = false, isHomeScreenCategory = true))
    }

    @Test
    fun `optional and reconfigurable configuration is not required`() {
        assertFalse(needsConfiguration(hasConfigureActivity = true, isConfigurationOptional = true, isReconfigurable = true))
    }

    @Test
    fun `configuration is required when only one of the optional features is set`() {
        assertTrue(needsConfiguration(hasConfigureActivity = true, isConfigurationOptional = true, isReconfigurable = false))
        assertTrue(needsConfiguration(hasConfigureActivity = true, isConfigurationOptional = false, isReconfigurable = true))
    }

    @Test
    fun `a provider without a configure activity never needs configuration`() {
        assertFalse(needsConfiguration(hasConfigureActivity = false, isConfigurationOptional = false, isReconfigurable = false))
    }
}
