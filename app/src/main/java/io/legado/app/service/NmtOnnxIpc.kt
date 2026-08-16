package io.legado.app.service

/** Internal protocol between the app process and the isolated NMT ONNX process. */
internal object NmtOnnxIpc {
    const val WHAT_TRANSLATE = 1
    const val WHAT_CANCEL = 2
    const val WHAT_CLOSE = 3

    const val RESULT_PROGRESS = 1
    const val RESULT_SUCCESS = 2
    const val RESULT_ERROR = 3
    const val RESULT_CANCELLED = 4

    const val KEY_REQUEST_ID = "request_id"
    const val KEY_TEXT = "text"
    const val KEY_DICTIONARY_JSON = "dictionary_json"
    const val KEY_CONFIG_JSON = "config_json"
    const val KEY_RESULT_JSON = "result_json"
    const val KEY_ERROR_CLASS = "error_class"
    const val KEY_ERROR_MESSAGE = "error_message"
    const val KEY_COMPLETED_SEGMENTS = "completed_segments"
    const val KEY_TOTAL_SEGMENTS = "total_segments"
    const val KEY_MIXED_TEXT = "mixed_text"
    const val KEY_RESULT_RECEIVER = "result_receiver"
}
