package core.data.launches

import core.util.MILLIS_PER_WEEK
import kotlin.math.log2

private const val RECENT_USE_HALF_LIFE_MS: Long = MILLIS_PER_WEEK

/** The half-lives between [fromEpochMs] and [toEpochMs] at the seven-day Recent use half-life, positive when [toEpochMs] is later. */
internal fun halfLivesBetween(fromEpochMs: Long, toEpochMs: Long): Double = (toEpochMs - fromEpochMs).toDouble() / RECENT_USE_HALF_LIFE_MS

/** The base-2 log of the stored score plus the half-lives between the epoch and its update. */
internal val LaunchCountRecord.recentUseRank: Double
    get() = log2(recentUseScore) + halfLivesBetween(fromEpochMs = 0L, toEpochMs = scoreUpdatedAt)
