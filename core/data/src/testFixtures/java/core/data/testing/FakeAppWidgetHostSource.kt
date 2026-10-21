package core.data.testing

import android.app.Activity
import core.data.widgets.AppWidgetId
import core.data.widgets.BindRequest
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.WidgetProvider
import core.data.widgets.WidgetProviderInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAppWidgetHostSource(initialProviders: List<WidgetProviderInfo> = emptyList(), bindResults: List<Boolean> = emptyList()) :
    IAppWidgetHostSource {
    val providersState: MutableStateFlow<List<WidgetProviderInfo>> = MutableStateFlow(initialProviders)
    override val providers: Flow<List<WidgetProviderInfo>> = providersState

    val allocatedIds: MutableList<AppWidgetId> = mutableListOf()
    val deletedIds: MutableList<AppWidgetId> = mutableListOf()
    val bindRequests: MutableList<BindRequest> = mutableListOf()
    val configureRequests: MutableList<AppWidgetId> = mutableListOf()
    var isListening: Boolean = false
        private set

    private val scriptedBindResults: ArrayDeque<Boolean> = ArrayDeque(bindResults)
    private var nextId: Int = 1

    override fun allocateId(): AppWidgetId = AppWidgetId(nextId++).also { id -> allocatedIds.add(id) }

    override fun deleteId(id: AppWidgetId) {
        deletedIds.add(id)
    }

    override fun bindIfAllowed(request: BindRequest): Boolean {
        bindRequests.add(request)
        return scriptedBindResults.removeFirstOrNull() ?: true
    }

    override fun providerInfo(id: AppWidgetId): WidgetProviderInfo? = null

    override fun providerInfo(provider: WidgetProvider, profileSerial: Long): WidgetProviderInfo? =
        providersState.value.firstOrNull { info -> info.provider == provider && info.profileSerial == profileSerial }

    override fun startListening() {
        isListening = true
    }

    override fun stopListening() {
        isListening = false
    }

    override fun startConfiguring(activity: Activity, id: AppWidgetId, requestCode: Int) {
        configureRequests.add(id)
    }

    fun setProviders(providers: List<WidgetProviderInfo>) {
        providersState.value = providers
    }
}
