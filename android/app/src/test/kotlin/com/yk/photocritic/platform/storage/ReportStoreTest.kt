package com.yk.photocritic.platform.storage

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReportStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 0L
    private val store by lazy { ReportStore(JsonFiles(tmp.root)) { now } }

    @Test
    fun countsPerUtcDay() =
        runBlocking {
            assertEquals(0, store.countToday())
            store.recordSent("r1")
            store.recordSent("r2")
            assertEquals(2, store.countToday())
            now = DAY_MS
            assertEquals(0, store.countToday())
            store.recordSent("r3")
            assertEquals(1, store.countToday())
            assertEquals(listOf("r1", "r2", "r3"), store.sentIds())
        }

    @Test
    fun keepsTheNewestIds() =
        runBlocking {
            repeat(ReportStore.MAX_IDS + 5) { store.recordSent("r$it") }
            val ids = store.sentIds()
            assertEquals(ReportStore.MAX_IDS, ids.size)
            assertEquals("r${ReportStore.MAX_IDS + 4}", ids.last())
        }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}
