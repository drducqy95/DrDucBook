package io.legado.app.domain.sourcehealth

data class SourceCheckRetentionPolicy(
    val maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS,
    val maxRunsPerSource: Int = DEFAULT_MAX_RUNS_PER_SOURCE,
) {
    init {
        require(maxAgeMillis >= 0L) { "maxAgeMillis must be non-negative" }
        require(maxRunsPerSource >= 1) { "maxRunsPerSource must be at least 1" }
    }

    companion object {
        const val DEFAULT_MAX_RUNS_PER_SOURCE = 100
        const val DEFAULT_MAX_AGE_MILLIS = 30L * 24L * 60L * 60L * 1000L
    }
}

data class SourceCheckCleanupResult(
    val inspectedRunCount: Int,
    val deletedRunCount: Int,
    val remainingRunCount: Int,
)
