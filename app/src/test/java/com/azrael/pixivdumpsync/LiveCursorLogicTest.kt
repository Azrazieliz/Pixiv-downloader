package com.azrael.pixivdumpsync

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveCursorLogicTest {
    @Test
    fun processesOnlyNewWorksOldestToNewest() {
        assertEquals(
            listOf("104", "105"),
            LiveCursorLogic.processingOrder(
                listOf("105", "104", "103", "102"),
                "103"
            )
        )
    }

    @Test
    fun fallsBackToNumericCursorWhenOldCursorIsMissing() {
        assertEquals(
            listOf("104", "105"),
            LiveCursorLogic.processingOrder(
                listOf("105", "104", "102"),
                "103"
            )
        )
    }

    @Test
    fun noCursorMeansEstablishBaselineWithoutBackfill() {
        assertEquals(
            emptyList<String>(),
            LiveCursorLogic.processingOrder(listOf("105", "104"), null)
        )
    }
}
