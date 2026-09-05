package io.legado.app.data.repository.ai

import android.util.Base64
import com.google.gson.JsonObject
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class SentinelTokens(
    val chatRequirementsToken: String? = null,
    val proofToken: String? = null,
)

object ChatGptWebSentinel {

    private val timeFormat = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT-0500 (Eastern Standard Time)'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("GMT-5")
    }

    private val navigatorKeys = listOf(
        "webdriver−false",
        "vendor−Google Inc.",
        "cookieEnabled−true",
        "pdfViewerEnabled−true",
        "hardwareConcurrency−32",
        "language−en-US",
        "appCodeName−Mozilla"
    )

    private val documentKeys = listOf("location")
    private val windowKeys = listOf("window", "self", "document")
    private val screenSizes = listOf(3000, 4000, 3120, 4160)
    private val coresList = listOf(8, 16, 24, 32)

    private fun getParseTime(): String {
        return synchronized(timeFormat) {
            timeFormat.format(Date())
        }
    }

    fun getConfig(userAgent: String): List<Any> {
        val screen = screenSizes.random()
        val parseTime = getParseTime()
        val navKey = navigatorKeys.random()
        val docKey = documentKeys.random()
        val winKey = windowKeys.random()
        val cores = coresList.random()
        val perf = System.nanoTime() / 1_000_000.0
        val uuid = UUID.randomUUID().toString()
        val timeDiff = System.currentTimeMillis() - perf

        return listOf(
            screen,
            parseTime,
            4294705152L,
            0,
            userAgent,
            "",
            "",
            "en-US",
            "en-US,es-US,en,es",
            0,
            navKey,
            docKey,
            winKey,
            perf,
            uuid,
            "",
            cores,
            timeDiff,
        )
    }

    fun getRequirementsToken(userAgent: String): String {
        val config = getConfig(userAgent)
        val seed = Math.random().toString()
        val (answer, _) = generateAnswer(seed, "0fffff", config)
        return "gAAAAAC$answer"
    }

    fun getAnswerToken(seed: String, diff: String, userAgent: String): String {
        val config = getConfig(userAgent)
        val (answer, _) = generateAnswer(seed, diff, config)
        return "gAAAAAB$answer"
    }

    fun generateAnswer(seed: String, diff: String, config: List<Any>): Pair<String, Boolean> {
        val seedBytes = seed.toByteArray(StandardCharsets.UTF_8)
        val targetDiff = hexToBytes(diff)
        val targetLen = targetDiff.size

        val p1 = "[${config[0]},\"${config[1]}\",${config[2]},".toByteArray(StandardCharsets.UTF_8)
        val p2 = ",\"${config[4]}\",\"${config[5]}\",\"${config[6]}\",\"${config[7]}\",\"${config[8]}\",".toByteArray(StandardCharsets.UTF_8)
        val p3 = ",\"${config[10]}\",\"${config[11]}\",\"${config[12]}\",${config[13]},\"${config[14]}\",\"${config[15]}\",${config[16]},${config[17]}]".toByteArray(StandardCharsets.UTF_8)

        val maxIterations = 500_000
        for (i in 0 until maxIterations) {
            val iBytes = i.toString().toByteArray(StandardCharsets.UTF_8)
            val jBytes = (i shr 1).toString().toByteArray(StandardCharsets.UTF_8)

            val fullJson = ByteArray(p1.size + iBytes.size + p2.size + jBytes.size + p3.size)
            var offset = 0
            System.arraycopy(p1, 0, fullJson, offset, p1.size); offset += p1.size
            System.arraycopy(iBytes, 0, fullJson, offset, iBytes.size); offset += iBytes.size
            System.arraycopy(p2, 0, fullJson, offset, p2.size); offset += p2.size
            System.arraycopy(jBytes, 0, fullJson, offset, jBytes.size); offset += jBytes.size
            System.arraycopy(p3, 0, fullJson, offset, p3.size)

            val baseEncodeStr = encodeBase64(fullJson)
            val baseEncodeBytes = baseEncodeStr.toByteArray(StandardCharsets.UTF_8)

            val hashInput = ByteArray(seedBytes.size + baseEncodeBytes.size)
            System.arraycopy(seedBytes, 0, hashInput, 0, seedBytes.size)
            System.arraycopy(baseEncodeBytes, 0, hashInput, seedBytes.size, baseEncodeBytes.size)

            val hash = sha3_512(hashInput)
            if (isHashValid(hash, targetDiff, targetLen)) {
                return Pair(baseEncodeStr, true)
            }
        }

        val fallback = "wQ8Lk5FbGpA2NcR9dShT6gYjU7VxZ4D" + encodeBase64("\"$seed\"".toByteArray(StandardCharsets.UTF_8))
        return Pair(fallback, false)
    }

    private fun isHashValid(hash: ByteArray, targetDiff: ByteArray, targetLen: Int): Boolean {
        for (k in 0 until targetLen) {
            val b = hash[k].toInt() and 0xFF
            val t = targetDiff[k].toInt() and 0xFF
            if (b < t) return true
            if (b > t) return false
        }
        return false
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = if (hex.length % 2 != 0) "0$hex" else hex
        val len = clean.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(clean[i], 16) shl 4) + Character.digit(clean[i + 1], 16)).toByte()
        }
        return data
    }

    private fun encodeBase64(data: ByteArray): String {
        return runCatching {
            Base64.encodeToString(data, Base64.NO_WRAP)
        }.getOrElse {
            java.util.Base64.getEncoder().encodeToString(data)
        }
    }

    fun sha3_512(data: ByteArray): ByteArray {
        val md = runCatching { MessageDigest.getInstance("SHA3-512") }.getOrNull()
        if (md != null) {
            return md.digest(data)
        }
        return PureKeccak.sha3_512(data)
    }

    suspend fun fetchSentinelTokens(
        baseUrl: String,
        accessToken: String,
        cookies: String,
        userAgent: String,
        deviceId: String,
    ): SentinelTokens = withContext(Dispatchers.IO) {
        runCatching {
            val host = if (baseUrl.isNotBlank()) baseUrl.trimEnd('/') else "https://chatgpt.com"
            val url = "$host/backend-api/sentinel/chat-requirements"
            val reqToken = getRequirementsToken(userAgent)
            val jsonPayload = JsonObject().apply {
                addProperty("p", reqToken)
            }.toString()

            val reqHeaders = mutableMapOf(
                "Authorization" to "Bearer $accessToken",
                "Content-Type" to "application/json",
                "User-Agent" to userAgent,
                "Origin" to "https://chatgpt.com",
                "Referer" to "https://chatgpt.com/",
                "oai-device-id" to deviceId,
                "oai-language" to "en-US",
            )
            if (cookies.isNotBlank()) {
                reqHeaders["Cookie"] = cookies
            }

            val requestBody = jsonPayload.toRequestBody("application/json;charset=utf-8".toMediaType())
            val resp = okHttpClient.newCallResponse {
                url(url)
                post(requestBody)
                addHeaders(reqHeaders)
            }

            if (!resp.isSuccessful) {
                return@runCatching SentinelTokens(null, null)
            }

            val bodyStr = resp.body.string()
            val root = runCatching { GSON.fromJson(bodyStr, JsonObject::class.java) }.getOrNull()
                ?: return@runCatching SentinelTokens(null, null)

            val chatRequirementsToken = root.get("token")?.asString

            var proofToken: String? = null
            val powObj = root.getAsJsonObject("proofofwork")
            if (powObj != null && powObj.get("required")?.asBoolean == true) {
                val seed = powObj.get("seed")?.asString.orEmpty()
                val diff = powObj.get("difficulty")?.asString.orEmpty()
                if (seed.isNotBlank() && diff.isNotBlank()) {
                    proofToken = getAnswerToken(seed, diff, userAgent)
                }
            }

            SentinelTokens(
                chatRequirementsToken = chatRequirementsToken,
                proofToken = proofToken,
            )
        }.getOrElse {
            SentinelTokens(null, null)
        }
    }
}

/**
 * Pure Kotlin Keccak-512 fallback in case platform MessageDigest lacks SHA3-512.
 */
internal object PureKeccak {
    private const val RATE_BYTES = 72 // (1600 - 1024) / 8

    private val RC = longArrayOf(
        0x0000000000000001UL.toLong(), 0x0000000000008082UL.toLong(), 0x800000000000808aUL.toLong(),
        0x8000000080008000UL.toLong(), 0x000000000000808bUL.toLong(), 0x0000000080000001UL.toLong(),
        0x8000000080008081UL.toLong(), 0x8000000000008009UL.toLong(), 0x000000000000008aUL.toLong(),
        0x0000000000000088UL.toLong(), 0x0000000080008009UL.toLong(), 0x000000008000000aUL.toLong(),
        0x000000008000808bUL.toLong(), 0x800000000000008bUL.toLong(), 0x8000000000008089UL.toLong(),
        0x8000000000008003UL.toLong(), 0x8000000000008002UL.toLong(), 0x8000000000000080UL.toLong(),
        0x000000000000800aUL.toLong(), 0x800000008000000aUL.toLong(), 0x8000000080008081UL.toLong(),
        0x8000000000008080UL.toLong(), 0x0000000080000001UL.toLong(), 0x8000000080008008UL.toLong()
    )

    private val ROT = arrayOf(
        intArrayOf(0, 36, 3, 41, 18),
        intArrayOf(1, 44, 10, 45, 2),
        intArrayOf(62, 6, 43, 15, 61),
        intArrayOf(28, 55, 25, 21, 56),
        intArrayOf(27, 20, 39, 8, 14),
    )

    fun sha3_512(input: ByteArray): ByteArray {
        val state = LongArray(25)
        var offset = 0
        val len = input.size

        // Absorb full blocks
        while (offset + RATE_BYTES <= len) {
            absorbBlock(state, input, offset)
            keccakF1600(state)
            offset += RATE_BYTES
        }

        // Final padded block
        val rem = len - offset
        val padBlock = ByteArray(RATE_BYTES)
        System.arraycopy(input, offset, padBlock, 0, rem)
        padBlock[rem] = (padBlock[rem].toInt() xor 0x06).toByte()
        padBlock[RATE_BYTES - 1] = (padBlock[RATE_BYTES - 1].toInt() xor 0x80).toByte()

        absorbBlock(state, padBlock, 0)
        keccakF1600(state)

        // Squeeze 64 bytes (8 Longs)
        val out = ByteArray(64)
        for (i in 0 until 8) {
            val v = state[i]
            for (j in 0 until 8) {
                out[i * 8 + j] = ((v ushr (j * 8)) and 0xFF).toByte()
            }
        }
        return out
    }

    private fun absorbBlock(state: LongArray, block: ByteArray, offset: Int) {
        for (i in 0 until 9) { // 9 * 8 = 72 bytes
            var v = 0L
            val base = offset + i * 8
            for (j in 0 until 8) {
                v = v or ((block[base + j].toLong() and 0xFFL) shl (j * 8))
            }
            state[i] = state[i] xor v
        }
    }

    private fun keccakF1600(a: LongArray) {
        val c = LongArray(5)
        val d = LongArray(5)
        val b = LongArray(25)

        for (round in 0 until 24) {
            // Theta
            for (x in 0 until 5) {
                c[x] = a[x] xor a[x + 5] xor a[x + 10] xor a[x + 15] xor a[x + 20]
            }
            for (x in 0 until 5) {
                d[x] = c[(x + 4) % 5] xor c[(x + 1) % 5].rotateLeft(1)
            }
            for (i in 0 until 25) {
                a[i] = a[i] xor d[i % 5]
            }

            // Rho & Pi
            for (x in 0 until 5) {
                for (y in 0 until 5) {
                    b[y + ((2 * x + 3 * y) % 5) * 5] = a[x + y * 5].rotateLeft(ROT[x][y])
                }
            }

            // Chi
            for (y in 0 until 5) {
                val y5 = y * 5
                for (x in 0 until 5) {
                    a[y5 + x] = b[y5 + x] xor (b[y5 + ((x + 1) % 5)].inv() and b[y5 + ((x + 2) % 5)])
                }
            }

            // Iota
            a[0] = a[0] xor RC[round]
        }
    }
}
