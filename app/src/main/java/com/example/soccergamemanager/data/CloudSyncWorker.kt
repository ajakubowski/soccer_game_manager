package com.example.soccergamemanager.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.soccergamemanager.SoccerManagerApp

class CloudSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val manager = (applicationContext as SoccerManagerApp).container.syncManager
        return if (manager.syncAllPairedTeams()) Result.success() else Result.retry()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "soccer-cloud-sync"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
