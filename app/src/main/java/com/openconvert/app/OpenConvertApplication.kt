package com.openconvert.app

import android.app.Application
import com.openconvert.app.data.local.OpenConvertDatabase
import com.openconvert.app.data.preferences.UserPreferences
import com.openconvert.app.data.repository.ConversionHistoryRepository
import com.openconvert.app.domain.work.ConversionRecovery
import com.openconvert.app.work.ConversionNotifier
import com.openconvert.app.work.ConversionScheduler
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OpenConvertApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database by lazy { OpenConvertDatabase.create(this) }
    val historyRepository by lazy { ConversionHistoryRepository(database.conversionDao()) }
    val userPreferences by lazy { UserPreferences(this) }
    val conversionScheduler by lazy { ConversionScheduler(this) }

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        ConversionNotifier.ensureChannel(this)
        conversionScheduler.cancelLegacyQueue()
        applicationScope.launch {
            val orphans = ConversionRecovery.reconcile(
                activeTasks = historyRepository.findActive(),
                activeWorkIds = conversionScheduler.activeTaskIds(),
            )
            orphans.forEach { historyRepository.save(it) }
        }
    }
}
