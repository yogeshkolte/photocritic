package com.yk.photocritic.phase0

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Firebase behaviour with collection off (design file 50, Q10). The
 * Crashlytics check runs in two instrumentation runs: [recordNonFatal]
 * first, then [unsentReportsAreHeld] in a new process.
 */
@RunWith(AndroidJUnit4::class)
class FirebaseChecksTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun collectionOffKeysAreInTheManifest() {
        val metaData =
            context.packageManager
                .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                .metaData
        for (key in listOf("firebase_analytics_collection_enabled", "firebase_crashlytics_collection_enabled")) {
            assertFalse(key, metaData.getBoolean(key, true))
        }
        assertFalse(FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled)
        observe("collection-off keys present; Crashlytics collection enabled = false")
    }

    @Test
    fun appInstanceIdWithCollectionOffAndOn() {
        val analytics = FirebaseAnalytics.getInstance(context)

        fun instanceId(): String =
            runCatching { Tasks.await(analytics.appInstanceId, 15, TimeUnit.SECONDS) }
                .fold({ it ?: "null" }, { "failed: ${it::class.simpleName}: ${it.message}" })
        val off = instanceId()
        analytics.setAnalyticsCollectionEnabled(true)
        val on = instanceId()
        analytics.setAnalyticsCollectionEnabled(false)
        val offAgain = instanceId()
        observe("appInstanceId collection off: $off")
        observe("appInstanceId collection on: $on")
        observe("appInstanceId collection off again: $offAgain")
    }

    @Test
    fun recordNonFatal() {
        FirebaseCrashlytics.getInstance().recordException(IllegalStateException("phase0 check: not a real crash"))
        Thread.sleep(3_000) // Crashlytics writes the event on a background thread.
        observe("non-fatal recorded with Crashlytics collection off")
    }

    @Test
    fun unsentReportsAreHeld() {
        val crashlytics = FirebaseCrashlytics.getInstance()
        val unsent = Tasks.await(crashlytics.checkForUnsentReports(), 15, TimeUnit.SECONDS)
        observe("Crashlytics unsent reports present: $unsent")
        crashlytics.deleteUnsentReports()
        assertEquals("unsent reports after delete", false, Tasks.await(crashlytics.checkForUnsentReports(), 15, TimeUnit.SECONDS))
    }
}
