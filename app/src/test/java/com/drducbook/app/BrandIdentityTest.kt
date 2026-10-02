package com.drducbook.app

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import io.legado.app.ui.main.Launcher0
import io.legado.app.ui.main.Launcher1
import io.legado.app.ui.main.Launcher2
import io.legado.app.ui.main.Launcher3
import io.legado.app.ui.main.Launcher4
import io.legado.app.ui.main.Launcher5
import io.legado.app.ui.main.Launcher6
import io.legado.app.ui.main.LauncherW
import io.legado.app.ui.main.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BrandIdentityTest {

    @Test
    fun applicationAndEveryLauncherUseDrDucBookBranding() {
        val context: Application = RuntimeEnvironment.getApplication()
        val packageManager = context.packageManager
        val applicationInfo = packageManager.getApplicationInfo(context.packageName, 0)

        assertEquals("DrDucBook", packageManager.getApplicationLabel(applicationInfo).toString())
        assertEquals(R.mipmap.ic_launcher, applicationInfo.icon)

        LAUNCHERS.forEach { launcher ->
            val activityInfo = packageManager.getActivityInfo(
                ComponentName(context, launcher),
                PackageManager.MATCH_DISABLED_COMPONENTS,
            )
            val expectedIcon = when (launcher) {
                MainActivity::class.java -> R.mipmap.ic_launcher
                LauncherW::class.java -> R.mipmap.launcherw
                Launcher0::class.java -> R.mipmap.launcher0
                Launcher1::class.java -> R.mipmap.launcher1
                Launcher2::class.java -> R.mipmap.launcher2
                Launcher3::class.java -> R.mipmap.launcher3
                Launcher4::class.java -> R.mipmap.launcher4
                Launcher5::class.java -> R.mipmap.launcher5
                Launcher6::class.java -> R.mipmap.launcher6
                else -> R.mipmap.ic_launcher
            }
            assertEquals(
                "Unexpected icon for ${launcher.name}",
                expectedIcon,
                activityInfo.iconResource,
            )
        }
    }

    private companion object {
        val LAUNCHERS = listOf(
            MainActivity::class.java,
            LauncherW::class.java,
            Launcher0::class.java,
            Launcher1::class.java,
            Launcher2::class.java,
            Launcher3::class.java,
            Launcher4::class.java,
            Launcher5::class.java,
            Launcher6::class.java,
        )
    }
}
