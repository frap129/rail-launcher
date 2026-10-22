package core.data.media

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import core.data.retryWithExponentialBackoff
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import timber.log.Timber

private const val SUBSCRIPTION_TIMEOUT_MS: Long = 5_000L

class IMediaSessionSourceImpl(context: Context, scope: CoroutineScope, private val artworkStore: MediaArtworkStore) : IMediaSessionSource {
    private val appContext: Context = context.applicationContext
    private val access: MutableStateFlow<Boolean> = MutableStateFlow(hasNotificationAccess())

    /** Bumped when notification access or the listener connection changes to rebuild the session stream. */
    private val subscriptionGeneration: MutableStateFlow<Long> = MutableStateFlow(0L)
    private val mainHandler: Handler = Handler(Looper.getMainLooper())

    override val hasAccess: StateFlow<Boolean> = access.asStateFlow()

    private val lock: Any = Any()
    private val controllers: MutableMap<MediaSessionKey, MediaController> = mutableMapOf()
    private val callbacks: MutableMap<MediaSessionKey, MediaController.Callback> = mutableMapOf()

    init {
        scope.launch {
            RailNotificationListener.connections.collect { connected ->
                if (connected) {
                    setAccess(true)
                } else {
                    restartSessions()
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val sessions: Flow<List<MediaSessionSnapshot>> = subscriptionGeneration
        .flatMapLatest {
            if (access.value) activeSessions() else flowOf(emptyList())
        }
        .conflate()
        .shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            replay = 1
        )

    private fun activeSessions(): Flow<List<MediaSessionSnapshot>> = callbackFlow {
        clearControllers()
        val manager: MediaSessionManager = appContext.getSystemService(MediaSessionManager::class.java)
        val listenerComponent: ComponentName = ComponentName(appContext, RailNotificationListener::class.java)
        val emitSessions: () -> Unit = { trySend(snapshots()) }
        val activeListener: MediaSessionManager.OnActiveSessionsChangedListener =
            MediaSessionManager.OnActiveSessionsChangedListener { activeControllers ->
                updateControllers(active = activeControllers.orEmpty(), onChanged = emitSessions)
            }
        var isListening: Boolean = false
        try {
            updateControllers(active = manager.getActiveSessions(listenerComponent).orEmpty(), onChanged = emitSessions)
            manager.addOnActiveSessionsChangedListener(activeListener, listenerComponent, mainHandler)
            isListening = true
        } catch (exception: SecurityException) {
            Timber.w(exception, "Lost MediaSession access while listing sessions")
            setAccess(false)
        } finally {
            if (!isListening) {
                clearControllers()
            }
        }
        awaitClose {
            try {
                manager.removeOnActiveSessionsChangedListener(activeListener)
            } catch (exception: SecurityException) {
                Timber.w(exception, "Could not remove the MediaSession listener")
            }
            clearControllers()
        }
    }.retryWithExponentialBackoff()

    override fun refreshAccess() {
        val granted: Boolean = hasNotificationAccess()
        if (access.value != granted) {
            setAccess(granted)
        }
    }

    private fun setAccess(granted: Boolean) {
        access.value = granted
        subscriptionGeneration.value += 1L
    }

    private fun restartSessions() {
        subscriptionGeneration.value += 1L
    }

    override fun execute(key: MediaSessionKey, command: MediaCommand) {
        val controls: MediaController.TransportControls = synchronized(lock) {
            controllers[key]?.transportControls
        } ?: return
        try {
            when (command) {
                MediaCommand.Previous -> controls.skipToPrevious()
                MediaCommand.Play -> controls.play()
                MediaCommand.Pause -> controls.pause()
                MediaCommand.Next -> controls.skipToNext()
            }
        } catch (exception: RuntimeException) {
            Timber.w(exception, "MediaSession command %s failed", command)
        }
    }

    override fun open(key: MediaSessionKey): Boolean {
        val controller: MediaController = synchronized(lock) { controllers[key] } ?: return false
        controller.sessionActivity?.let { activity ->
            try {
                activity.send()
                return true
            } catch (exception: PendingIntent.CanceledException) {
                Timber.w(exception, "The player's session activity was cancelled")
            }
        }
        val launchIntent: Intent = appContext.packageManager.getLaunchIntentForPackage(controller.packageName) ?: return false
        return try {
            appContext.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (exception: ActivityNotFoundException) {
            Timber.w(exception, "No launch intent for %s", controller.packageName)
            false
        } catch (exception: SecurityException) {
            Timber.w(exception, "No permission to launch %s", controller.packageName)
            false
        }
    }

    private fun updateControllers(active: List<MediaController>, onChanged: () -> Unit) {
        synchronized(lock) {
            val ordered: LinkedHashMap<MediaSessionKey, MediaController> = LinkedHashMap()
            val newlyRegistered: MutableList<Pair<MediaSessionKey, MediaController>> = mutableListOf()
            try {
                active.forEach { controller ->
                    val key: MediaSessionKey = controller.key()
                    val existing: MediaController? = controllers[key]
                    if (existing != null) {
                        ordered[key] = existing
                    } else {
                        val callback: MediaController.Callback = createCallback(
                            key = key,
                            controller = controller,
                            onChanged = onChanged
                        )
                        callbacks[key] = callback
                        try {
                            controller.registerCallback(callback, mainHandler)
                        } catch (exception: RuntimeException) {
                            callbacks.remove(key)
                            throw exception
                        }
                        newlyRegistered += key to controller
                        ordered[key] = controller
                    }
                }
            } catch (exception: RuntimeException) {
                newlyRegistered.forEach { (key, controller) ->
                    unregister(key = key, controller = controller)
                }
                throw exception
            }
            controllers.forEach { (key, controller) ->
                if (key !in ordered) {
                    unregister(key = key, controller = controller)
                }
            }
            controllers.clear()
            controllers.putAll(ordered)
        }
        onChanged()
    }

    private fun createCallback(key: MediaSessionKey, controller: MediaController, onChanged: () -> Unit): MediaController.Callback =
        object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) = onChanged()

            override fun onPlaybackStateChanged(state: PlaybackState?) = onChanged()

            override fun onSessionDestroyed() {
                synchronized(lock) {
                    if (controllers[key] === controller) {
                        controllers.remove(key)
                        callbacks.remove(key)
                    }
                }
                controller.unregisterCallback(this)
                onChanged()
            }
        }

    private fun unregister(key: MediaSessionKey, controller: MediaController) {
        callbacks.remove(key)?.let { callback -> controller.unregisterCallback(callback) }
    }

    private fun clearControllers() {
        synchronized(lock) {
            controllers.forEach { (key, controller) ->
                unregister(key = key, controller = controller)
            }
            controllers.clear()
            callbacks.clear()
        }
    }

    private fun snapshots(): List<MediaSessionSnapshot> {
        val active: List<MediaController> = synchronized(lock) { controllers.values.toList() }
        return active.mapNotNull { controller -> controller.toSnapshot() }
    }

    private fun MediaController.toSnapshot(): MediaSessionSnapshot? {
        val metadata: MediaMetadata = metadata ?: return null
        val title: String = metadata.string(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata.description.title?.toString()?.takeIf { text -> text.isNotBlank() }
            ?: return null
        val artist: String? = metadata.string(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.string(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata.description.subtitle?.toString()?.takeIf { text -> text.isNotBlank() }
        val playbackState: PlaybackState? = playbackState
        val artworkBitmap: Bitmap? = metadata.artworkBitmap()
        val artworkUri: Uri? = metadata.artworkUri()
        val artworkKey: String = listOf(
            packageName,
            title,
            artist.orEmpty(),
            artworkUri?.toString().orEmpty(),
            artworkBitmap?.generationId?.toString().orEmpty()
        ).joinToString(separator = "|")
        return MediaSessionSnapshot(
            key = key(),
            packageName = packageName,
            appLabel = packageLabel(packageName = packageName),
            title = title,
            artist = artist,
            artwork = artworkStore.put(
                key = artworkKey,
                bitmap = artworkBitmap,
                uri = artworkUri
            ),
            state = playbackState.toPlaybackStatus(),
            actions = playbackState.toMediaCommands()
        )
    }

    private fun MediaMetadata.string(key: String): String? = getString(key)?.takeIf { text -> text.isNotBlank() }

    private fun MediaMetadata.artworkBitmap(): Bitmap? =
        getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: getBitmap(MediaMetadata.METADATA_KEY_ART)

    private fun MediaMetadata.artworkUri(): Uri? = listOf(
        MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
        MediaMetadata.METADATA_KEY_ART_URI
    )
        .firstNotNullOfOrNull { key -> string(key = key)?.let(Uri::parse) }

    private fun MediaController.key(): MediaSessionKey = MediaSessionKey(sessionToken.toString())

    private fun packageLabel(packageName: String): String = try {
        val info = appContext.packageManager.getApplicationInfo(packageName, 0)
        appContext.packageManager.getApplicationLabel(info).toString()
    } catch (exception: PackageManager.NameNotFoundException) {
        packageName
    }

    private fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(appContext).contains(appContext.packageName)
}

private fun PlaybackState?.toPlaybackStatus(): PlaybackStatus = when (this?.state) {
    PlaybackState.STATE_PLAYING -> PlaybackStatus.PLAYING
    PlaybackState.STATE_PAUSED -> PlaybackStatus.PAUSED
    PlaybackState.STATE_BUFFERING -> PlaybackStatus.BUFFERING
    PlaybackState.STATE_CONNECTING -> PlaybackStatus.CONNECTING
    PlaybackState.STATE_FAST_FORWARDING -> PlaybackStatus.FAST_FORWARDING
    PlaybackState.STATE_REWINDING -> PlaybackStatus.REWINDING
    PlaybackState.STATE_SKIPPING_TO_NEXT -> PlaybackStatus.SKIPPING_TO_NEXT
    PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> PlaybackStatus.SKIPPING_TO_PREVIOUS
    PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> PlaybackStatus.SKIPPING_TO_QUEUE_ITEM
    PlaybackState.STATE_STOPPED -> PlaybackStatus.STOPPED
    PlaybackState.STATE_ERROR -> PlaybackStatus.ERROR
    else -> PlaybackStatus.NONE
}

private fun PlaybackState?.toMediaCommands(): Set<MediaCommand> {
    val actionFlags: Long = this?.actions ?: 0L
    return buildSet {
        if (actionFlags and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L) {
            add(MediaCommand.Previous)
        }
        if (actionFlags and PlaybackState.ACTION_PLAY_PAUSE != 0L) {
            add(MediaCommand.Play)
            add(MediaCommand.Pause)
        } else {
            if (actionFlags and PlaybackState.ACTION_PLAY != 0L) {
                add(MediaCommand.Play)
            }
            if (actionFlags and PlaybackState.ACTION_PAUSE != 0L) {
                add(MediaCommand.Pause)
            }
        }
        if (actionFlags and PlaybackState.ACTION_SKIP_TO_NEXT != 0L) {
            add(MediaCommand.Next)
        }
    }
}
