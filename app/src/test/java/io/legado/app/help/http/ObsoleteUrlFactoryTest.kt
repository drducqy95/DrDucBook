package io.legado.app.help.http

import okhttp3.OkHttpClient
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URL

class ObsoleteUrlFactoryTest {

    @Test
    fun httpsDelegateAcceptsNullRequestPropertyValues() {
        val connection = ObsoleteUrlFactory.OkHttpsURLConnection(
            URL("https://example.com"),
            OkHttpClient(),
        )

        connection.setRequestProperty("X-Optional", null)
        connection.addRequestProperty("X-Optional", null)

        assertNull(connection.getRequestProperty("X-Optional"))
    }
}
