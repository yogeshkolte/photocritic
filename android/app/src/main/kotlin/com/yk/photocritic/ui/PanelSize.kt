package com.yk.photocritic.ui

/** The conversation panel's three sizes; drag the bar or use its arrows to move one step. */
enum class PanelSize {
    /** Only the bar and the composer, so the photo can be seen. */
    MINIMIZED,

    /** Sized to its content, up to 45% of the screen. */
    COLLAPSED,
    EXPANDED,
    ;

    fun larger() = entries[minOf(ordinal + 1, entries.lastIndex)]

    fun smaller() = entries[maxOf(ordinal - 1, 0)]
}
