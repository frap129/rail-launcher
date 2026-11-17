package core.data.shortcuts

import core.data.apps.model.AppProfile
import java.io.Closeable

/** The one LauncherApps callback Rail keeps registered while it listens for Shortcut and package changes. */
interface IShortcutListener {
    /**
     * Listens for Shortcut and package changes to any App in [apps] and calls [onChange] on the
     * main looper for each change, returning the handle that stops listening. Returns null when
     * the callback cannot be registered.
     */
    fun listen(apps: Collection<AppProfile>, onChange: () -> Unit): Closeable?
}
