package core.data.testing

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import timber.log.Timber

/** Records the messages and throwables of every Timber call made while it is recording. */
class TimberLogRecorder : Timber.Tree() {
    private val recordedEntries: MutableList<String> = mutableListOf()

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        recordedEntries += message
        t?.let { throwable -> recordedEntries += throwable.toString() }
    }

    fun record(action: () -> Unit) {
        Timber.plant(this)
        try {
            action()
        } finally {
            Timber.uproot(this)
        }
    }

    suspend fun recordSuspending(action: suspend () -> Unit) {
        Timber.plant(this)
        try {
            action()
        } finally {
            Timber.uproot(this)
        }
    }

    /** Asserts something was logged and no entry contains any of [identifiers]. */
    fun assertLoggedWithout(vararg identifiers: String) {
        assertTrue("Expected a log entry", recordedEntries.isNotEmpty())
        val leakedIdentifier: String? = identifiers.firstOrNull { identifier ->
            recordedEntries.any { entry -> entry.contains(identifier) }
        }
        assertNull("Log leaked $leakedIdentifier", leakedIdentifier)
    }
}
