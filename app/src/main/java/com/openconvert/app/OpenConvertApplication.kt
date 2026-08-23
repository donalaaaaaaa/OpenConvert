package com.openconvert.app

import android.app.Application
import com.openconvert.app.data.local.OpenConvertDatabase
import com.openconvert.app.data.preferences.UserPreferences
import com.openconvert.app.data.repository.ConversionHistoryRepository
import com.openconvert.app.domain.work.ConversionRecovery
import com.openconvert.app.domain.model.ConversionTask
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
    val presetStore by lazy {
        com.openconvert.app.data.repository.PresetStore(database.presetDao())
    }
    val conversionScheduler by lazy { ConversionScheduler(this) }
    val batchScheduler by lazy { BatchScheduler(this) }
    val cacheManager by lazy { com.openconvert.app.domain.cache.CacheManager(this) }
    val tempWorkspace by lazy { com.openconvert.app.domain.work.TempWorkspaceManager(this) }
    val conversionHost by lazy { com.openconvert.app.ui.ConversionHost(this, applicationScope) }

    override fun onCreate() {
        super.onCreate()
        AppCopy.bind(this)
        PDFBoxResourceLoader.init(applicationContext)
        ConversionNotifier.ensureChannel(this)
        conversionScheduler.cancelLegacyQueue()
        applicationScope.launch {
            // 首次启动播种内置预设（计划书 §八）。空库才写，不覆盖用户调整。
            runCatching { presetStore.seedIfEmpty() }
            recoverOrphans()
        }
    }

    /**
     * 把「Room 里还是 PENDING/RUNNING，但 WorkManager 已经没了」的任务标成失败。
     * 系统杀进程、崩溃、用户强行停止之后，下次启动走这里。
     * 暂停中的批量任务不参与（它们的 work 是被主动取消的）。
     */
    suspend fun recoverOrphans(): List<ConversionTask> {
        val active = historyRepository.findActive()
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
        return orphans
    }
}
