package com.yk.photocritic.core.providers.shared

import com.yk.photocritic.core.RepoRoot
import com.yk.photocritic.core.providers.PromptKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SystemPromptTest {
    @Test
    fun `prompts come from shared prompts`() {
        assertEquals(RepoRoot.shared("prompts/system.txt").trim(), SystemPrompt.base)
        assertEquals(RepoRoot.shared("prompts/VERSION").trim(), SystemPrompt.version)
        for (kind in PromptKind.entries) {
            assertTrue(SystemPrompt.forKind(kind).endsWith(RepoRoot.shared("prompts/${kind.fileName}").trim()), kind.name)
        }
    }
}
