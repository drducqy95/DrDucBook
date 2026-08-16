package io.legado.app.service

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import io.legado.app.help.config.BookshelfAutomationConfig
import java.util.concurrent.TimeUnit

object BookshelfAutomationScheduler {

    fun applyConfig(context: Context) {
        if (BookshelfAutomationConfig.enabled) {
            schedule(context)
        } else {
            cancel(context)
        }
    }

    fun schedule(context: Context): Boolean {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val intervalMillis = TimeUnit.HOURS.toMillis(
            BookshelfAutomationConfig.intervalHours.toLong()
        )
        val job = JobInfo.Builder(
            PERIODIC_JOB_ID,
            ComponentName(context, BookshelfAutomationJobService::class.java),
        )
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setPeriodic(intervalMillis)
            .setBackoffCriteria(
                TimeUnit.MINUTES.toMillis(30),
                JobInfo.BACKOFF_POLICY_EXPONENTIAL,
            )
            .build()
        return scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS
    }

    fun cancel(context: Context) {
        context.getSystemService(JobScheduler::class.java).cancel(PERIODIC_JOB_ID)
    }

    const val PERIODIC_JOB_ID = 0x4C47_5550
}
