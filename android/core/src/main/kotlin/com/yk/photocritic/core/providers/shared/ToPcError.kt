package com.yk.photocritic.core.providers.shared

import com.yk.photocritic.core.providers.PcError
import com.yk.photocritic.core.providers.PcPhase

/** Builds a [PcError] without classifying it (design §7, file 25). */
object ToPcError {
    fun of(
        phase: PcPhase,
        httpStatus: Int?,
        providerCode: String?,
        requestId: String?,
        message: String,
        apiKey: String,
    ): PcError =
        PcError(
            phase = phase,
            httpStatus = httpStatus,
            providerCode = providerCode?.take(PROVIDER_CODE_LIMIT),
            requestId = requestId?.take(REQUEST_ID_LIMIT),
            detail = Redact.text(message, apiKey),
        )

    private const val PROVIDER_CODE_LIMIT = 100
    private const val REQUEST_ID_LIMIT = 200
}
