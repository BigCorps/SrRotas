package com.srrotas.app

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** BR-06: banner without polling and no redesign of reader offer cards. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HistoryPendingIndicator1126Test {
    private val app get() = RuntimeEnvironment.getApplication()
    private fun texts(root: View): List<String> = when (root) {
        is TextView -> listOf(root.text.toString())
        is ViewGroup -> (0 until root.childCount).flatMap { texts(root.getChildAt(it)) }
        else -> emptyList()
    }

    @After fun clear() = HistoryReviewStore109.clear(app)

    @Test fun digitalizedTabWarnsOnlyWhenPrivateReviewContainsRealPendingRecords() {
        HistoryReviewStore109.clear(app)
        val panel = RideHistoryPanel027035(app)
        assertFalse(texts(panel).contains("Digitalizados (!)"))
        HistoryReviewStore109.save(app,
            listOf(UberCompletedRide026(
                sourceKey = "synthetic-review-001", capturedAt = "2026-11-25T15:00:00Z",
                occurredAt = null, fare = 8.0, serviceType = "uberx",
                pickupLabel = null, destinationLabel = null, confidence = 0.40,
            )), framesRead = 1, framesWithRides = 1)
        panel.refresh()
        assertTrue(texts(panel).contains("Digitalizados (!)"))
        HistoryReviewStore109.clear(app)
        panel.refresh()
        assertFalse(texts(panel).contains("Digitalizados (!)"))
    }
}
