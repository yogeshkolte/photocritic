package com.yk.photocritic.core.providers.shared

import com.yk.photocritic.core.providers.PcPhase

/** The one generic message and the "Show details" labels (G12, file 25). English; kept together for translation. */
object UserMessages {
    const val GENERIC = "Something went wrong. Please try again."

    const val LABEL_WHERE = "Where"
    const val LABEL_HTTP_STATUS = "HTTP status"
    const val LABEL_PROVIDER_CODE = "Provider code"
    const val LABEL_REQUEST_ID = "Request ID"
    const val LABEL_DETAILS = "Details"

    fun phase(phase: PcPhase): String =
        when (phase) {
            PcPhase.BEFORE_RESPONSE -> "Before the answer"
            PcPhase.MID_STREAM -> "During the answer"
            PcPhase.FINISH -> "At the end of the answer"
        }
}
