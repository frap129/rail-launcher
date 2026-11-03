package feature.launcher

import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import core.data.shortcuts.ShortcutKey
import core.data.shortcuts.ShortcutLaunchable
import core.data.shortcuts.toShortcutLaunchable
import core.ui.R as CoreUiR
import core.ui.theme.AppTheme
import core.util.logTypeName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber

/** Confirms a Pin request from another app before adding its Shortcut as a Standalone shortcut. */
class PinShortcutActivity : ComponentActivity() {
    internal val viewModel: PinShortcutViewModel by viewModel()
    private val notifier: IPinShortcutNotifier by inject()

    private var pinShortcut: PinShortcut? = null
    private val dialogContent: MutableStateFlow<PinShortcutDialogContent?> = MutableStateFlow(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null && viewModel.addState.value != PinShortcutAddState.Idle) {
            return finish()
        }
        val launcherApps: LauncherApps = getSystemService(LauncherApps::class.java)
        val request: LauncherApps.PinItemRequest = readPinRequest(intent = intent, launcherApps = launcherApps) ?: return finish()
        if (request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
            Timber.w("Ignoring a pin request of type %d", request.requestType)
            return finish()
        }
        if (!request.isValid) {
            Timber.w("A Shortcut pin request is no longer valid")
            return finishWithAddError()
        }
        val shortcut: ShortcutInfo? = request.shortcutInfo
        if (shortcut == null) {
            Timber.w("A Shortcut pin request has no Shortcut")
            return finishWithAddError()
        }
        val key: ShortcutKey = ShortcutKey(
            packageName = shortcut.getPackage(),
            id = shortcut.id,
            profile = shortcut.userHandle ?: Process.myUserHandle()
        )
        pinShortcut = PinShortcut(request = request, key = key)
        setContent {
            val addState: PinShortcutAddState by viewModel.addState.collectAsState()
            val content: PinShortcutDialogContent? by dialogContent.collectAsState()
            LaunchedEffect(addState) {
                if (addState.isFinished) {
                    finish()
                }
            }
            content?.let { currentContent ->
                AppTheme {
                    PinShortcutDialog(
                        shortcutLabel = currentContent.shortcutLabel,
                        sourceAppName = currentContent.sourceAppName,
                        shortcutIcon = currentContent.shortcutIcon,
                        isAdding = addState.isAdding,
                        onAdd = ::addShortcut,
                        onCancel = ::cancel
                    )
                }
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val launchable: ShortcutLaunchable = shortcut.toShortcutLaunchable(owner = key.toAppProfile())
            dialogContent.value = PinShortcutDialogContent(
                shortcutLabel = launchable.defaultName,
                sourceAppName = readSourceAppName(launcherApps = launcherApps, shortcut = shortcut, profile = key.profile),
                shortcutIcon = readIcon(launcherApps = launcherApps, shortcut = shortcut, profile = key.profile)
            )
        }
    }

    private fun readPinRequest(intent: Intent, launcherApps: LauncherApps): LauncherApps.PinItemRequest? = try {
        launcherApps.getPinItemRequest(intent)
    } catch (exception: SecurityException) {
        logRequestFailure(exception = exception)
        null
    } catch (exception: IllegalArgumentException) {
        logRequestFailure(exception = exception)
        null
    }

    private fun logRequestFailure(exception: Exception) {
        Timber.w("Could not read a Shortcut pin request: %s", exception.logTypeName())
    }

    private fun finishWithAddError() {
        notifier.showAddError()
        finish()
    }

    private fun readSourceAppName(launcherApps: LauncherApps, shortcut: ShortcutInfo, profile: UserHandle): String = runCatching {
        launcherApps.getApplicationInfo(shortcut.getPackage(), 0, profile).loadLabel(packageManager).toString()
    }.getOrElse { exception ->
        Timber.w("Could not read the app that sent a Shortcut pin request: %s", exception.logTypeName())
        shortcut.getPackage()
    }

    private fun readIcon(launcherApps: LauncherApps, shortcut: ShortcutInfo, profile: UserHandle): Drawable? {
        val shortcutIcon: Drawable? = runCatching {
            launcherApps.getShortcutIconDrawable(shortcut, resources.displayMetrics.densityDpi)
        }.getOrNull()
        if (shortcutIcon != null) {
            return shortcutIcon
        }
        val appIcon: Drawable? = runCatching {
            launcherApps.getApplicationInfo(shortcut.getPackage(), 0, profile).loadIcon(packageManager)
        }.getOrNull()
        if (appIcon == null) {
            Timber.w("Could not read an icon for a Shortcut pin request")
        }
        return appIcon
    }

    private fun cancel() {
        if (!viewModel.addState.value.isAdding) {
            finish()
        }
    }

    internal fun addShortcut() {
        val currentPinShortcut: PinShortcut = pinShortcut ?: return
        viewModel.add(
            key = currentPinShortcut.key,
            accept = { currentPinShortcut.request.accept() }
        )
    }
}

private data class PinShortcut(val request: LauncherApps.PinItemRequest, val key: ShortcutKey)

private data class PinShortcutDialogContent(val shortcutLabel: String, val sourceAppName: String, val shortcutIcon: Drawable?)

@Composable
private fun PinShortcutDialog(
    shortcutLabel: String,
    sourceAppName: String,
    shortcutIcon: Drawable?,
    isAdding: Boolean,
    onAdd: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = if (shortcutIcon != null) {
            {
                Image(
                    modifier = Modifier.size(48.dp),
                    painter = rememberDrawablePainter(drawable = shortcutIcon),
                    contentDescription = stringResource(R.string.pin_shortcut_icon)
                )
            }
        } else {
            null
        },
        title = { Text(text = shortcutLabel) },
        text = { Text(text = stringResource(R.string.pin_shortcut_from, sourceAppName)) },
        confirmButton = {
            TextButton(onClick = onAdd, enabled = !isAdding) {
                Text(text = stringResource(CoreUiR.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !isAdding) {
                Text(text = stringResource(CoreUiR.string.cancel))
            }
        }
    )
}
