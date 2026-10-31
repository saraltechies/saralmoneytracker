package com.saraltechies.saralmoneytracker.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPolicyTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000 * day

    @Test
    fun waitsForRealUse() {
        assertFalse(ReviewPolicy.shouldAsk(ReviewPolicy.MIN_GOOD_MOMENTS - 1, 0, 0, now))
        assertTrue(ReviewPolicy.shouldAsk(ReviewPolicy.MIN_GOOD_MOMENTS, 0, 0, now))
    }

    @Test
    fun spacesOutAndCapsPrompts() {
        assertFalse(ReviewPolicy.shouldAsk(100, 1, now - 10 * day, now))
        assertTrue(ReviewPolicy.shouldAsk(100, 1, now - 31 * day, now))
        assertFalse(ReviewPolicy.shouldAsk(100, ReviewPolicy.MAX_PROMPTS, 0, now))
    }
}
