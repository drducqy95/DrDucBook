package io.legado.app.di

import com.drducbook.app.cloud.SupabasePublicConfig
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.koin.dsl.koinApplication

class AccountModuleBindingTest {

    @Test
    fun publicSupabaseConfigIsAvailableToAccountViewModelFactory() {
        val koin = koinApplication { modules(appModule) }.koin

        assertNotNull(koin.get<SupabasePublicConfig>())
    }
}
