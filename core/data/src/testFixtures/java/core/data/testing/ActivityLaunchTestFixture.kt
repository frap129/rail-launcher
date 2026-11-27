package core.data.testing

import android.content.Context
import android.content.Intent
import io.mockk.every

private const val ACTIVITY_START_FAILURE_MESSAGE: String = "The target cannot open"

/** Stubs Activity starts on a test [Context], failing those matched by [shouldFail]. */
fun Context.failActivityStarts(shouldFail: (Intent) -> Boolean = { true }, onStart: (Intent) -> Unit = {}) {
    every { startActivity(any()) } answers {
        val inputIntent: Intent = firstArg()
        onStart(inputIntent)
        if (shouldFail(inputIntent)) {
            throw IllegalStateException(ACTIVITY_START_FAILURE_MESSAGE)
        }
    }
}
