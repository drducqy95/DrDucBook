package io.legado.app.service

import android.app.Dialog
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.WindowManager.BadTokenException
import androidx.annotation.RequiresApi
import com.drducbook.app.R
import io.legado.app.constant.IntentAction
import io.legado.app.domain.usecase.WebServiceAccessUseCase
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext


/**
 * web服务快捷开关
 */
@RequiresApi(Build.VERSION_CODES.N)
class WebTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            when (intent?.action) {
                IntentAction.start -> qsTile?.run {
                    state = Tile.STATE_ACTIVE
                    updateTile()
                }

                IntentAction.stop -> qsTile?.run {
                    state = Tile.STATE_INACTIVE
                    updateTile()
                }
            }
        } catch (e: Exception) {
            e.printOnDebug()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onStartListening() {
        super.onStartListening()
        serviceScope.launch {
            val allowed = runCatching {
                GlobalContext.get().get<WebServiceAccessUseCase>().isAllowed()
            }.getOrDefault(false)
            qsTile?.run {
                state = when {
                    WebService.isRun -> Tile.STATE_ACTIVE
                    allowed -> Tile.STATE_INACTIVE
                    else -> Tile.STATE_UNAVAILABLE
                }
                updateTile()
            }
        }
    }

    override fun onClick() {
        super.onClick()
        if (WebService.isRun) {
            WebService.stop(this)
        } else {
            serviceScope.launch {
                val allowed = runCatching {
                    GlobalContext.get().get<WebServiceAccessUseCase>().isAllowed()
                }.getOrDefault(false)
                if (!allowed) {
                    qsTile?.run {
                        state = Tile.STATE_UNAVAILABLE
                        updateTile()
                    }
                    toastOnUi(getString(R.string.web_service_premium_required))
                    return@launch
                }
                startWebService()
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startWebService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val dialog = Dialog(this, R.style.AppTheme_Transparent)
            dialog.setOnShowListener {
                try {
                    WebService.startForeground(this)
                } catch (e: ForegroundServiceStartNotAllowedException) {
                    e.printStackTrace()
                }
                dialog.dismiss()
            }
            try {
                showDialog(dialog)
            } catch (e: BadTokenException) {
                e.printStackTrace()
            }
        } else {
            WebService.start(this)
        }
    }
}
