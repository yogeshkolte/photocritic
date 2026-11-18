package com.yk.photocritic.platform.telemetry

/** Analytics parameter types; `shared/telemetry/events.json` names them `string` and `integer`. */
enum class ParamType(
    val wire: String,
) {
    STRING("string"),
    INTEGER("integer"),
}

/** The only events the app sends (file 33), checked against `shared/telemetry/events.json`. */
enum class PcEvent(
    val wire: String,
    val params: Map<String, ParamType>,
) {
    SESSION_START("pc_session_start", strings("provider", "model") + ints("config_version")),
    IMAGE_PREPARED(
        "pc_image_prepared",
        strings("source", "input_format") + ints("prepare_ms", "sent_long_edge_px", "sent_bytes"),
    ),
    QUESTION_SENT("pc_question_sent", strings("provider", "model", "starter_prompt") + ints("turn_index")),
    ANSWER_RECEIVED(
        "pc_answer_received",
        strings("provider", "model", "finish_reason") +
            ints("time_to_first_token_ms", "total_ms", "input_tokens", "output_tokens"),
    ),
    REQUEST_FAILED("pc_request_failed", strings("provider", "model", "phase") + ints("http_status")),
    REPORT_SUBMITTED("pc_report_submitted", strings("provider", "model", "category", "result")),
    CONFIG_FETCH("pc_config_fetch", strings("result") + ints("config_version", "ms")),
}

private fun strings(vararg names: String) = names.associateWith { ParamType.STRING }

private fun ints(vararg names: String) = names.associateWith { ParamType.INTEGER }
