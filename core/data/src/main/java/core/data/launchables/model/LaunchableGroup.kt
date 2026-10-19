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
    }

    /** Launchables sharing a first character; digit-prefixed names share "#". */
    data class InitialGroup(override val label: String, override val items: List<Launchable>) : Listed

    /** Rail's own Launchables, last on the Rail. */
    data class Builtin(override val items: List<Launchable>) : Listed {
        override val label: String = "⌂"
    }
}
