package core.data.launchables.model

/** An entry on the Rail. */
sealed interface LaunchableGroup {
    val label: String

    /** The Star: first on the Rail, stands for Home. Rail-only, holds no Launchables. */
    data object Star : LaunchableGroup {
        override val label: String = "★"
    }

    /** A group shown in the App List. */
    sealed interface Listed : LaunchableGroup {
        val items: List<Launchable>

        /** A copy of this group holding [items]. */
        fun withItems(items: List<Launchable>): Listed
    }

    /** Launchables sharing a first character; digit-prefixed names share "#". */
    data class InitialGroup(override val label: String, override val items: List<Launchable>) : Listed {
        override fun withItems(items: List<Launchable>): LaunchableGroup.Listed = copy(items = items)
    }

    /** Rail's own Launchables, last on the Rail. */
    data class Builtin(override val items: List<Launchable>) : Listed {
        override val label: String = "⌂"

        override fun withItems(items: List<Launchable>): LaunchableGroup.Listed = copy(items = items)
    }
}

/**
 * A stable identity for the group in the App List. A name starting with a Rail
 * label (e.g. "⌂") forms an Initial group that shares that label, so the key
 * includes the group kind.
 */
fun LaunchableGroup.Listed.appListKey(): String = when (this) {
    is LaunchableGroup.Builtin -> "builtin:$label"
    is LaunchableGroup.InitialGroup -> "initial:$label"
}
