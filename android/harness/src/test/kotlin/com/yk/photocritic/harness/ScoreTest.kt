package com.yk.photocritic.harness

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScoreTest {
    @Test
    fun `exact text in a fence scores zero, ignoring case and spacing`() {
        assertEquals(0.0, Score.cer("Here it is:\n```text\nlot83  969.95\nZONE3\n```\nA label.", "LOT83 969.95\nZONE3"))
    }

    @Test
    fun `an answer without a fence is scored whole`() {
        assertEquals(0.0, Score.cer("ROW7", "ROW7"))
    }

    @Test
    fun `edits are counted against the expected length`() {
        assertEquals(1, Score.distance("KXP", "KXQ"))
        assertEquals(3, Score.distance("", "abc"))
        assertEquals(0.25, Score.cer("ABCE", "ABCD"))
        assertEquals(1.0, Score.cer("", "ABCD"))
    }

    @Test
    fun `csv fields with commas or quotes are quoted`() {
        assertEquals("a", Run.csvField("a"))
        assertEquals("", Run.csvField(null))
        assertEquals("\"a,\"\"b\"\"\"", Run.csvField("a,\"b\""))
    }
}
