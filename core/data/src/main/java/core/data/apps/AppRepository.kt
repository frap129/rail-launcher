package core.data.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.os.UserManager
import core.util.launchInBackground
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

class AppRepository(context: Context) {
    private val packageManager = context.packageManager
    private val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    private val _apps: MutableStateFlow<MutableList<App>> = MutableStateFlow(mutableListOf())
    val apps: StateFlow<List<App>> = _apps.asStateFlow()

    private val appChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            launchInBackground {
                val action = intent.action
                if (Intent.ACTION_PACKAGE_ADDED == action) {
                    val packageName = intent.data!!.schemeSpecificPart
                    Timber.d("App installed: $packageName")
                    _apps.value = _apps.value.apply {
                        addAll(getAppInfo(packageName))
                    }
                } else if (Intent.ACTION_PACKAGE_REMOVED == action) {
                    val packageName = intent.data!!.schemeSpecificPart
                    Timber.d("App uninstalled: $packageName")
                    // Remove app from list, ensuring that it's removed from the proper profile
                    _apps.value = _apps.value.apply {
                        val matchingApps = getAppInfo(packageName)
                        _apps.value = _apps.value.apply {
                            when (matchingApps.isEmpty()) {
                                true -> removeIf { it.packageName == packageName }

                                false -> matchingApps.forEach { app ->
                                    removeIf { it.packageName == app.packageName && it.profile != app.profile }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    init {
        launchInBackground {
            _apps.value = getAppsList(context)
            apps.value.forEach {
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
