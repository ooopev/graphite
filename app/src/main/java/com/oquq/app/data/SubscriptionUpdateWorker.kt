package com.oquq.app.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.oquq.app.core.parser.SubscriptionParser
import com.oquq.app.core.repo.ProfileRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Battery-aware subscription auto-update.
 *
 * WorkManager (not a service/wakelock): deferrable, batched by the OS with
 * other maintenance, runs only on unmetered connectivity — near-zero battery
 * footprint compared to naive periodic pings.
 */
class SubscriptionUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = ProfileRepository.get(applicationContext)
        var failures = 0
        repo.subscriptions().first().forEach { sub ->
            if (!sub.autoUpdate) return@forEach
            SubscriptionParser.fetch(sub.url)
                .onSuccess { update ->
                    repo.updateSubscription(SubscriptionParser.applyToSubscription(sub, update))
                    repo.replaceSubscriptionProfiles(sub.id, update.profiles)
                }
                .onFailure { failures++ }
        }
        return if (failures == 0) Result.success()
        else if (runAttemptCount < 3) Result.retry() else Result.failure()
    }

    companion object {
        private const val WORK = "oquq_subscription_update"

        /** Re-schedules (or cancels) the periodic work from settings changes. */
        fun schedule(context: Context, enabled: Boolean, intervalHours: Int) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(WORK)
                return
            }
            val request = PeriodicWorkRequestBuilder<SubscriptionUpdateWorker>(
                intervalHours.coerceIn(1, 24 * 7), TimeUnit.HOURS,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
