package core.data.launchables.model

/** Customized Launchable names by Launchable key. */
@JvmInline
value class LaunchableNames(private val namesByKey: Map<String, String?>) {
    operator fun get(key: String): String? = namesByKey[key]
}
