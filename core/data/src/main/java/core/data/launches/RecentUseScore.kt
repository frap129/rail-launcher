package core.data.launches

import core.util.MILLIS_PER_WEEK
import kotlin.math.pow

private const val RECENT_USE_HALF_LIFE_MS: Long = MILLIS_PER_WEEK

/** [score] decayed from [scoreUpdatedAt] to [nowEpochMs], halving every seven days. */
internal fun decayRecentUseScore(score: Double, scoreUpdatedAt: Long, nowEpochMs: Long): Double =
    score * 2.0.pow(-((nowEpochMs - scoreUpdatedAt).toDouble() / RECENT_USE_HALF_LIFE_MS))
