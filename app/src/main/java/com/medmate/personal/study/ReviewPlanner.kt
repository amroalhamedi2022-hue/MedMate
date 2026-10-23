package com.medmate.personal.study

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Deterministic, offline spaced review for personal alpha. This is NOT FSRS.
 * Ratings: 0 Again (10m), 1 Hard (1d), 2 Good (growing), 3 Easy (faster).
 * Clock is an argument so scheduling is testable and not timezone-dependent.
 */
data class ReviewState(
    val dueAt: Long,
    val intervalDays: Int = 0,
    val ease: Double = 2.3,
    val repetitions: Int = 0,
    val lapses: Int = 0,
)

object ReviewPlanner {
    const val DAY_MS = 86_400_000L
    const val TEN_MIN_MS = 600_000L

    fun next(before: ReviewState, rating: Int, now: Long): ReviewState {
        require(rating in 0..3) { "Invalid review rating" }
        require(now >= 0L) { "Invalid review clock" }
        require(before.intervalDays >= 0 && before.repetitions >= 0 && before.lapses >= 0)
        val previous = before.intervalDays
        if (rating == 0) return ReviewState(
            dueAt = now + TEN_MIN_MS,
            intervalDays = 0,
            ease = max(1.3, before.ease - 0.2),
            repetitions = 0,
            lapses = before.lapses + 1,
        )
        val nextEase = (before.ease + when (rating) {
            1 -> -0.15
            2 -> 0.0
            else -> 0.1
        }).coerceIn(1.3, 3.2)
        val days = when (rating) {
            1 -> max(1, if (previous == 0) 1 else (previous * 0.65).roundToLong().toInt())
            2 -> when (before.repetitions) {
                0 -> 1
                1 -> 3
                else -> (max(1, previous) * nextEase).roundToLong().toInt()
            }
            else -> when (before.repetitions) {
                0 -> 4
                1 -> 7
                else -> (max(1, previous) * (nextEase + 0.35)).roundToLong().toInt()
            }
        }.coerceIn(1, 3650)
        return ReviewState(
            dueAt = now + days.toLong() * DAY_MS,
            intervalDays = days,
            ease = nextEase,
            repetitions = min(before.repetitions + 1, 100_000),
            lapses = before.lapses,
        )
    }
}
