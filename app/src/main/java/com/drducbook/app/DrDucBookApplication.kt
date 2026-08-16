package com.drducbook.app

import com.drducbook.app.cloud.SupabaseClientProvider
import io.legado.app.App

class DrDucBookApplication : App() {

    override fun onCreate() {
        super.onCreate()
        if (!isNmtOnnxProcess) SupabaseClientProvider.client
    }
}
