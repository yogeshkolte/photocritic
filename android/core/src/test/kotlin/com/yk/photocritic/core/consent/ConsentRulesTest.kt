package com.yk.photocritic.core.consent

import com.yk.photocritic.core.providers.Capabilities
import com.yk.photocritic.core.providers.ImagePlacement
import com.yk.photocritic.core.providers.Streaming
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConsentRulesTest {
    private val cloud = Capabilities(true, ImagePlacement.ANY_TURN, Streaming.TOKENS, usageReporting = false, dataLeavesDevice = true)

    @Test
    fun `cloud providers need a granted record at or above the current text version`() {
        assertFalse(ConsentRules.canSend(cloud, grantedVersion = null, requiredVersion = 1))
        assertTrue(ConsentRules.canSend(cloud, grantedVersion = 1, requiredVersion = 1))
        assertTrue(ConsentRules.canSend(cloud, grantedVersion = 2, requiredVersion = 1))
        assertFalse(ConsentRules.canSend(cloud, grantedVersion = 1, requiredVersion = 2))
    }

    @Test
    fun `an on-device provider never needs consent`() {
        assertTrue(ConsentRules.canSend(cloud.copy(dataLeavesDevice = false), grantedVersion = null, requiredVersion = 3))
    }
}
