package io.legado.app.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.data.repository.EntitledMediaDownloadGateway
import io.legado.app.data.repository.MediaDownloadRepository
import io.legado.app.domain.gateway.MediaDownloadGateway
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class MediaDependencyInjectionTest {

    @Test
    fun mediaDownloadDecoratorResolvesWithoutRequestingItself() {
        val koin = GlobalContext.get()
        val repository = koin.get<MediaDownloadRepository>()
        val gateway = koin.get<MediaDownloadGateway>()

        assertTrue(gateway is EntitledMediaDownloadGateway)
        assertNotSame(repository, gateway)
    }
}
