package com.yk.photocritic.platform.storage

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class JsonFilesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Serializable
    data class Doc(
        val n: Int = 0,
    )

    private fun files() = JsonFiles(tmp.root)

    @Test
    fun missingCorruptAndOk() =
        runBlocking {
            val files = files()
            assertEquals(FileRead.Missing, files.readState("a.json", Doc.serializer()))
            File(tmp.root, "a.json").writeText("{not json")
            assertEquals(FileRead.Unreadable, files.readState("a.json", Doc.serializer()))
            assertEquals(Doc(7), files.read("a.json", Doc.serializer()) { Doc(7) })
            files.write("a.json", Doc.serializer(), Doc(3))
            assertEquals(FileRead.Ok(Doc(3)), files.readState("a.json", Doc.serializer()))
        }

    @Test
    fun unknownKeysAreIgnored() =
        runBlocking {
            File(tmp.root, "a.json").writeText("""{"n":2,"later":true}""")
            assertEquals(Doc(2), files().read("a.json", Doc.serializer()) { Doc() })
        }

    @Test
    fun updateStartsFromDefaultAndWrites() =
        runBlocking {
            val files = files()
            assertEquals(Doc(1), files.update("a.json", Doc.serializer(), ::Doc) { it.copy(n = it.n + 1) })
            assertEquals(Doc(2), files.update("a.json", Doc.serializer(), ::Doc) { it.copy(n = it.n + 1) })
        }

    @Test
    fun aFailedWriteKeepsTheOldFile() =
        runBlocking {
            val files = files()
            files.write("a.json", Doc.serializer(), Doc(1))
            // A directory where the temporary file must go makes the write fail.
            File(tmp.root, "a.json.new").mkdirs()
            val failed =
                try {
                    files.write("a.json", Doc.serializer(), Doc(2))
                    false
                } catch (_: IOException) {
                    true
                }
            assertTrue(failed)
            assertEquals(Doc(1), files.read("a.json", Doc.serializer()) { Doc() })
        }

    @Test
    fun anInterruptedWriteKeepsTheOldFile() =
        runBlocking {
            val files = files()
            files.write("a.json", Doc.serializer(), Doc(1))
            // What a crash between startWrite and finishWrite leaves behind.
            File(tmp.root, "a.json.new").writeText("""{"n":""")
            assertEquals(Doc(1), files.read("a.json", Doc.serializer()) { Doc() })
        }

    @Test
    fun utcTimes() {
        assertEquals("1970-01-01T00:00:00.000Z", UtcTime.iso(0))
        assertEquals("2026-11-18", UtcTime.day(1_794_960_000_000))
    }
}
