package com.yk.photocritic.core.providers.shared

import com.yk.photocritic.core.providers.PromptKind

/** The base prompt and prompt-kind texts from `shared/prompts/` (design file 11). */
object SystemPrompt {
    val base: String = read("system.txt")
    val version: String = read("VERSION")

    fun forKind(kind: PromptKind): String = base + "\n\n" + read(kind.fileName)

    private fun read(name: String): String =
        checkNotNull(SystemPrompt::class.java.getResource("/$name")) { "missing prompt resource $name" }
            .readText()
            .trim()
}
