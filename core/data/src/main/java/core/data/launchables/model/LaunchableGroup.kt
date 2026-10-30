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

    /** Rail's own Launchables, after the Initial groups. */
    data class Builtin(override val items: List<Launchable>) : Listed {
        override val label: String = "⌂"

        override fun withItems(items: List<Launchable>): LaunchableGroup.Listed = copy(items = items)
    }

    /**
     * The Private Space container, last on the Rail. It holds the private Apps
     * only while Unlocked and keeps them out of every other group.
     */
    sealed interface PrivateSpace : Listed {
        /** Whether the Private Space is quiet, so it shows only its container row. */
        val isLocked: Boolean

        override val label: String get() = LABEL

        /** The Locked container, which holds no private Apps. */
        data object Locked : PrivateSpace {
            override val isLocked: Boolean = true
            override val items: List<Launchable> = emptyList()

            override fun withItems(items: List<Launchable>): Listed = if (items.isEmpty()) this else Unlocked(items = items)
        }

        /** The Unlocked container holding its private Apps, A-Z. */
        data class Unlocked(override val items: List<Launchable>) : PrivateSpace {
            override val isLocked: Boolean = false

            override fun withItems(items: List<Launchable>): Listed = copy(items = items)
        }

        companion object {
            /** A label apart from the other Rail labels and initials; the Rail and App List draw it as a shield. */
            const val LABEL: String = "⚿"
        }
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
    is LaunchableGroup.PrivateSpace -> "private:$label"
}

/** The groups a picker may list: the Listed groups without the Locked Private Space container. */
fun List<LaunchableGroup>.toPickerGroups(): List<LaunchableGroup.Listed> = filterIsInstance<LaunchableGroup.Listed>()
    .filterNot { group -> group is LaunchableGroup.PrivateSpace && group.isLocked }
