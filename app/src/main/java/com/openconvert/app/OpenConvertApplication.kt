package com.openconvert.app

import android.app.Application
import com.openconvert.app.data.local.OpenConvertDatabase
import com.openconvert.app.data.preferences.UserPreferences
import com.openconvert.app.data.repository.ConversionHistoryRepository
import com.openconvert.app.domain.work.ConversionRecovery
import com.openconvert.app.work.BatchScheduler
import com.openconvert.app.work.ConversionNotifier
import com.openconvert.app.work.ConversionScheduler
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OpenConvertApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database by lazy { OpenConvertDatabase.create(this) }
    val historyRepository by lazy {
        ConversionHistoryRepository(database.conversionDao(), database.batchJobDao())
    }
    val userPreferences by lazy { UserPreferences(this) }
    val conversionScheduler by lazy { ConversionScheduler(this) }
    val batchScheduler by lazy { BatchScheduler(this) }
    val cacheManager by lazy { com.openconvert.app.domain.cache.CacheManager(this) }

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        ConversionNotifier.ensureChannel(this)
        conversionScheduler.cancelLegacyQueue()
        applicationScope.launch {
            val active = historyRepository.findActive()
            // 暂停中的批量任务不参与孤儿恢复（它们的 work 被主动取消，等待恢复）。
            val pausedBatchIds = database.batchJobDao()
                .observeAll()
                .let { flow -> flow.first() }
                .filter { it.status == "PAUSED" }
                .map { it.id }
                .toSet()
            val recoverable = active.filter { task ->
                task.payload.batchId == null || task.payload.batchId !in pausedBatchIds
            }
            val orphans = ConversionRecovery.reconcile(
                activeTasks = recoverable,
                activeWorkIds = conversionScheduler.activeTaskIds(),
            )
            orphans.forEach { historyRepository.save(it) }
        }
    }
}
