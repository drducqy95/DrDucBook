package io.legado.app.help.media

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import io.legado.app.domain.model.ResolvedMediaVariant
import io.legado.app.domain.model.ResolvedSubtitleTrack
import io.legado.app.help.http.okHttpClient
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class ResolvedMediaPlayer private constructor(
    val player: ExoPlayer,
    private val dataSourceFactory: OkHttpDataSource.Factory,
) {

    fun prepare(
        variant: ResolvedMediaVariant,
        subtitles: List<ResolvedSubtitleTrack>,
        playWhenReady: Boolean,
    ) {
        dataSourceFactory.setDefaultRequestProperties(variant.headers)
        val subtitleConfigurations = subtitles.map { subtitle ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.uri))
                .setId(subtitle.id)
                .setLabel(subtitle.label)
                .setLanguage(subtitle.language.takeIf(String::isNotBlank))
                .setMimeType(subtitle.mimeType.takeIf(String::isNotBlank))
                .setSelectionFlags(if (subtitle.isDefault) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }
        val mediaItem = MediaItem.Builder()
            .setMediaId(variant.id)
            .setUri(variant.uri)
            .setMimeType(
                variant.mimeType.takeIf {
                    it.isNotBlank() && !it.endsWith("/*")
                }
            )
            .setSubtitleConfigurations(subtitleConfigurations)
            .build()
        player.setMediaItem(mediaItem)
        player.playWhenReady = playWhenReady
        player.prepare()
    }

    fun release() {
        player.release()
    }

    companion object {
        fun create(context: Context): ResolvedMediaPlayer {
            val client = okHttpClient.newBuilder()
                .callTimeout(45, TimeUnit.SECONDS)
                .build()
            val dataSourceFactory = OkHttpDataSource.Factory(client)
            val schemeAwareDataSourceFactory = DefaultDataSource.Factory(context, dataSourceFactory)
            val mediaSourceFactory = DefaultMediaSourceFactory(context)
                .setDataSourceFactory(schemeAwareDataSourceFactory)
                .setLiveTargetOffsetMs(5_000)
            val player = ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
            return ResolvedMediaPlayer(player, dataSourceFactory)
        }
    }
}
