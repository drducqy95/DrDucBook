package io.legado.app.help.media

object MediaPlaybackPositionPolicy {

    fun prepareStartPosition(
        persistedAbsoluteMs: Long,
        clipStartMs: Long?,
        clipEndMs: Long?,
        requestedRelativeMs: Long,
    ): Long {
        val clipStart = clipStartMs ?: 0L
        val persisted = persistedAbsoluteMs.coerceAtLeast(0L)
        val persistedInClip = persisted.takeIf { position ->
            position >= clipStart && (clipEndMs == null || position < clipEndMs)
        }
        return persistedInClip ?: seekAbsolute(
            relativeMs = requestedRelativeMs,
            clipStartMs = clipStartMs,
            clipEndMs = clipEndMs,
        )
    }

    fun seekAbsolute(
        relativeMs: Long,
        clipStartMs: Long?,
        clipEndMs: Long?,
    ): Long {
        val absolute = (clipStartMs ?: 0L) + relativeMs.coerceAtLeast(0L)
        return clipEndMs?.let(absolute::coerceAtMost) ?: absolute
    }

    fun relativePosition(
        absoluteMs: Long,
        clipStartMs: Long?,
    ): Long = (absoluteMs.coerceAtLeast(0L) - (clipStartMs ?: 0L)).coerceAtLeast(0L)

    fun relativeDuration(
        rawDurationMs: Long,
        clipStartMs: Long?,
        clipEndMs: Long?,
    ): Long {
        val clipStart = clipStartMs ?: 0L
        return clipEndMs?.let { end ->
            (end - clipStart).coerceAtLeast(0L)
        } ?: (rawDurationMs.coerceAtLeast(0L) - clipStart).coerceAtLeast(0L)
    }
}
