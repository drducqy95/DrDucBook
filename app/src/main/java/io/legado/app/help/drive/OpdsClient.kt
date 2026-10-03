package io.legado.app.help.drive

import io.legado.app.domain.model.OpdsCatalog
import io.legado.app.help.http.okHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream

class OpdsClient(private val client: OkHttpClient = okHttpClient) {

    suspend fun fetchCatalog(url: String): OpdsCatalog = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/atom+xml,application/xml,text/xml,*/*")
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            error("HTTP ${response.code} when fetching OPDS catalog from $url")
        }
        val body = response.body?.string() ?: error("Empty OPDS response from $url")
        OpdsParser.parse(body, url)
    }

    suspend fun downloadBook(url: String): InputStream = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            error("HTTP ${response.code} when downloading book from $url")
        }
        response.body?.byteStream() ?: error("Failed to open book stream from $url")
    }
}
