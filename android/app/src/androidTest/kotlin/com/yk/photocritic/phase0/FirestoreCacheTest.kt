package com.yk.photocritic.phase0

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.LocalCacheSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.PersistentCacheSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * The report write keeps no text on disk with `MemoryCacheSettings` (design
 * file 36). The network is off, so the write stays pending in the local cache;
 * the persistent-cache run shows the scan would find it.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreCacheTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun pendingWriteOnDisk(cache: LocalCacheSettings): Boolean {
        val marker = "pc-phase0-marker-${UUID.randomUUID()}"
        val app = FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, "phase0-${UUID.randomUUID()}")
        val db = FirebaseFirestore.getInstance(app)
        db.firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(cache).build()
        Tasks.await(db.disableNetwork(), 10, TimeUnit.SECONDS)
        db.collection("phase0_checks").document().set(mapOf("text" to marker))
        Thread.sleep(3_000)
        val found = containsBytes(context.dataDir, marker.toByteArray())
        Tasks.await(db.terminate(), 10, TimeUnit.SECONDS)
        app.delete()
        return found
    }

    private fun containsBytes(
        dir: File,
        needle: ByteArray,
    ): Boolean =
        dir.walkTopDown().filter { it.isFile }.any { file ->
            val bytes = runCatching { file.readBytes() }.getOrDefault(ByteArray(0))
            bytes.indices.any { start -> start + needle.size <= bytes.size && needle.indices.all { bytes[start + it] == needle[it] } }
        }

    @Test
    fun memoryCacheLeavesNoTextOnDisk() {
        val found = pendingWriteOnDisk(MemoryCacheSettings.newBuilder().build())
        observe("memory cache: report text found on disk = $found")
        assertFalse(found)
    }

    @Test
    fun persistentCacheWouldStoreIt() {
        val found = pendingWriteOnDisk(PersistentCacheSettings.newBuilder().build())
        observe("persistent cache (control): report text found on disk = $found")
        assertTrue("the scan should find a persisted pending write", found)
    }
}
