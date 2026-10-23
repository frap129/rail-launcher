package core.util

/**
 * How long a flow started with `SharingStarted.WhileSubscribed` keeps its upstream
 * subscription after the last collector leaves, so transient collector loss (for
 * example a configuration change) does not restart it.
 */
const val WHILE_SUBSCRIBED_TIMEOUT_MS: Long = 5_000L
