package com.yk.photocritic.core.consent

import com.yk.photocritic.core.providers.Capabilities

/** The consent gate (G13, file 30). */
object ConsentRules {
    /**
     * True when the provider keeps data on the device, or when the user
     * granted consent to [requiredVersion] or a later text. [grantedVersion]
     * is the version of the current granted record, null when there is
     * none. "Not now" is never recorded, so it never counts.
     */
    fun canSend(
        capabilities: Capabilities,
        grantedVersion: Int?,
        requiredVersion: Int,
    ): Boolean = !capabilities.dataLeavesDevice || (grantedVersion != null && grantedVersion >= requiredVersion)
}
