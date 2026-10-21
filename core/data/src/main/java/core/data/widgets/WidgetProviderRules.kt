package core.data.widgets

/** Whether a provider is offered by Rail's picker. */
fun isPickerProvider(isOwnPackage: Boolean, isHomeScreenCategory: Boolean): Boolean = !isOwnPackage && isHomeScreenCategory

/** Whether placing a provider first requires its configuration activity. */
fun needsConfiguration(hasConfigureActivity: Boolean, isConfigurationOptional: Boolean, isReconfigurable: Boolean): Boolean =
    hasConfigureActivity && !(isConfigurationOptional && isReconfigurable)
