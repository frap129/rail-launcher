package core.data.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.os.UserManager
import core.data.apps.model.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class IAppRepositoryImpl(context: Context, private val scope: CoroutineScope) : IAppRepository {
    private val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    private val _apps: MutableStateFlow<List<App>?> = MutableStateFlow(null)
    override val apps: StateFlow<List<App>?> = _apps.asStateFlow()

    private val appChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            scope.launch(Dispatchers.IO) {
                if (_apps.value == null) {
                    return@launch
                }
                val action = intent.action
                if (Intent.ACTION_PACKAGE_ADDED == action) {
                    val packageName = intent.data!!.schemeSpecificPart
                    val addedApps: List<App> = getAppInfo(packageName)
                    Timber.d("App installed: $packageName")
                    _apps.update { current ->
                        (current ?: emptyList()).toMutableList().apply {
                            removeAll { app -> addedApps.any { added -> added.key == app.key } }
                            addAll(addedApps)
                        }
                    }
                } else if (Intent.ACTION_PACKAGE_REMOVED == action) {
                    // App updates arrive as REMOVED with EXTRA_REPLACING; keep the app.
                    if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                        return@launch
                    }
                    val packageName = intent.data!!.schemeSpecificPart
                    val remainingApps: List<App> = getAppInfo(packageName)
                    Timber.d("App uninstalled: $packageName")
                    // Remove the app from the profiles where it is no longer installed.
                    _apps.update { current ->
                        (current ?: emptyList()).toMutableList().apply {
                            if (remainingApps.isEmpty()) {
                                removeIf { it.packageName == packageName }
                            } else {
                                remainingApps.forEach { remaining ->
                                    removeIf { it.packageName == remaining.packageName && it.profile != remaining.profile }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    init {
        scope.launch(Dispatchers.IO) {
            _apps.value = getAppsList(context)
            apps.value?.forEach {
                Timber.d("added ${it.name}")
            }
        }

        context.registerReceiver(
            appChangeReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }
        )
    }

    private fun getAppInfo(packageName: String?): List<App> {
        val appList: MutableList<App> = mutableListOf()
        try {
            for (profile in userManager.userProfiles) {
                for (info in launcherApps.getActivityList(packageName, profile)) {
                    val app = App(
                        defaultName = info.label.toString(),
                        profile = info.user,
                        componentName = info.componentName
                    )
                    appList.add(app)
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
        return appList
    }

    private fun getAppsList(context: Context): MutableList<App> {
        val appList: MutableList<App> = getAppInfo(null).toMutableList()

        // Ignore this app
        appList.removeIf { it.packageName == context.applicationInfo.packageName }

        return appList
    }
}
