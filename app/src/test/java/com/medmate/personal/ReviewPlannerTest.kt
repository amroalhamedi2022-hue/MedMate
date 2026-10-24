package com.medmate.personal

import com.medmate.personal.study.ReviewPlanner
import com.medmate.personal.study.ReviewState
import org.junit.Assert.*
import org.junit.Test

class ReviewPlannerTest {
    private val now = 1_000_000_000L

    @Test fun initialGoodDueTomorrow() {
        val x = ReviewPlanner.next(ReviewState(now), 2, now)
        assertEquals(1, x.intervalDays)
        assertEquals(now + ReviewPlanner.DAY_MS, x.dueAt)
        assertEquals(1, x.repetitions)
    }

    @Test fun goodAfterFirstReviewDueInThreeDays() {
        val first = ReviewPlanner.next(ReviewState(now), 2, now)
        val next = ReviewPlanner.next(first, 2, now + ReviewPlanner.DAY_MS)
        assertEquals(3, next.intervalDays)
    }

    @Test fun againRequeuesQuicklyAndCountsLapse() {
        val previous = ReviewState(now, 17, 2.3, 3, 0)
        val next = ReviewPlanner.next(previous, 0, now)
        assertEquals(now + ReviewPlanner.TEN_MIN_MS, next.dueAt)
        assertEquals(0, next.repetitions)
        assertEquals(1, next.lapses)
        assertEquals(0, next.intervalDays)
    }

    @Test fun easyIsLongerThanGoodForNewCards() {
        val initial = ReviewState(now)
        assertTrue(ReviewPlanner.next(initial, 3, now).dueAt > ReviewPlanner.next(initial, 2, now).dueAt)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidRating() { ReviewPlanner.next(ReviewState(now), 9, now) }
}
