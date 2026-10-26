package core.data.widgets

import android.app.Activity
import android.os.UserHandle
import core.data.icons.model.Icon
import kotlinx.coroutines.flow.Flow

/** The system id of an App Widget hosted by Rail. */
@JvmInline
value class AppWidgetId(val value: Int)

/** A size in dp, used for an App Widget's default and resize bounds. */
data class WidgetSize(val widthDp: Int, val heightDp: Int)

/** Whether a placed App Widget can currently be rendered. */
enum class WidgetAvailability {
    Available,
    Gone,
    Updating,
    Disabled,
    ProfilePaused,
    Unknown
}

/** Everything needed to bind an allocated App Widget id to a provider. */
data class BindRequest(val id: AppWidgetId, val provider: WidgetProvider, val profileSerial: Long)

/** An App Widget provider offered by another app, resolved for Rail's UI. */
data class WidgetProviderInfo(
    val provider: WidgetProvider,
    val profileSerial: Long,
    val appPackageName: String,
    val appLabel: String,
    val appIcon: Icon,
    val label: String,
    val description: String?,
    val defaultSize: WidgetSize,
    val cellSize: WidgetSize?,
    val minHeightDp: Int,
    val minResizeHeightDp: Int,
    val maxResizeHeightDp: Int?,
    val needsConfiguration: Boolean,
    val isReconfigurable: Boolean,
    val hasConfigureActivity: Boolean,
    val isWorkProfile: Boolean,
    val isVerticallyResizable: Boolean = false
)

/**
 * The only data source that talks to the Android App Widget service. The
 * ViewModel and its fakes work with the pure value types above.
 */
interface IAppWidgetHostSource {
    fun allocateId(): AppWidgetId

    fun deleteId(id: AppWidgetId)

    fun bindIfAllowed(request: BindRequest): Boolean

    /** Home-screen providers from every profile, excluding Rail's own providers. */
    val providers: Flow<List<WidgetProviderInfo>>

    /** Emits when the host or a package/profile change may have altered providers. */
    val providerChanges: Flow<Unit>

    /** Every App Widget id the system currently attributes to Rail's host. */
    fun boundIds(): Set<AppWidgetId>

    /** The current availability of a placed App Widget. */
    fun availability(id: AppWidgetId, provider: WidgetProvider, profileSerial: Long): WidgetAvailability

    /** Provider details looked up directly from the system for a bound App Widget. */
    fun providerInfo(id: AppWidgetId): WidgetProviderInfo?

    /** The serial number of [profile], or null when the system has no such profile. */
    fun profileSerial(profile: UserHandle): Long?

    fun startListening()

    fun stopListening()

    /** Starts the provider's configuration activity; failures throw. */
    fun startConfiguring(activity: Activity, id: AppWidgetId, requestCode: Int)

    companion object {
        const val HOST_ID: Int = 1024
    }
}

/** The provider label, falling back to the provider class when it is no longer installed. */
fun WidgetProviderInfo?.labelOr(provider: WidgetProvider): String = this?.label ?: provider.className

/** The provider app icon, falling back to the app package when it is no longer installed. */
fun WidgetProviderInfo?.iconOr(provider: WidgetProvider): Icon = this?.appIcon ?: Icon.ApplicationIcon(packageName = provider.packageName)
