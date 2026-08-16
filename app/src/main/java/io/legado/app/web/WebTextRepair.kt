package io.legado.app.web

import io.legado.app.utils.TextEncodingRepair

/**
 * Repairs the small set of mojibake patterns found in older video plugins.
 * The value is decoded only when every character can be represented as a
 * single byte and the UTF-8 result removes the mojibake markers. This keeps
 * normal Vietnamese and CJK titles unchanged.
 */
object WebTextRepair {
    fun repair(value: String?): String? = TextEncodingRepair.repair(value)
}
