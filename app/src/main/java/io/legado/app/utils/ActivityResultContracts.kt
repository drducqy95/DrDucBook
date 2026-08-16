package io.legado.app.utils

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import splitties.init.appCtx

fun <T> ActivityResultLauncher<T?>.launch() {
    launch(null)
}

class SelectImageContract : ActivityResultContract<Int?, SelectImageContract.Result>() {

    private val delegate = ActivityResultContracts.PickVisualMedia()
    private var requestCode: Int? = null
    private var useFallback = false

    override fun createIntent(context: Context, input: Int?): Intent {
        requestCode = input
        val intent = Intent(Intent.ACTION_GET_CONTENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("image/*")
        useFallback = intent.resolveActivity(appCtx.packageManager) == null
        return if (useFallback) {
            val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            delegate.createIntent(context, request)
        } else {
            intent
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Result {
        val isFallback = useFallback
        useFallback = false

        val uri = if (isFallback) {
            delegate.parseResult(resultCode, intent)
        } else if (resultCode == RESULT_OK) {
            intent?.data
        } else null

        return Result(requestCode, uri)
    }

    data class Result(
        val requestCode: Int?,
        val uri: Uri? = null
    )

}

class StartActivityContract(private val cls: Class<*>) :
    ActivityResultContract<(Intent.() -> Unit)?, ActivityResult>() {

    override fun createIntent(context: Context, input: (Intent.() -> Unit)?): Intent {
        val intent = Intent(context, cls)
        input?.let {
            intent.apply(input)
        }
        return intent
    }

    override fun parseResult(
        resultCode: Int, intent: Intent?
    ): ActivityResult {
        return ActivityResult(resultCode, intent)
    }

}

class FilteredOpenDocumentContract(
    private val primaryMimeType: String,
    private val localOnly: Boolean = true,
    private val persistableAccess: Boolean = true,
) : ActivityResultContract<Array<String>, Uri?>() {

    override fun createIntent(context: Context, input: Array<String>): Intent {
        val action = if (persistableAccess) {
            Intent.ACTION_OPEN_DOCUMENT
        } else {
            Intent.ACTION_GET_CONTENT
        }
        return Intent(action)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(primaryMimeType)
            .apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (persistableAccess) {
                    addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }
                putExtra(Intent.EXTRA_LOCAL_ONLY, localOnly)
                if (input.isNotEmpty()) {
                    putExtra(Intent.EXTRA_MIME_TYPES, input)
                }
            }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return intent?.data.takeIf { resultCode == RESULT_OK }
    }
}
