package com.openconvert.app.data.preferences

import android.content.Context
import com.openconvert.app.domain.model.QualityPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _imageQuality = MutableStateFlow(read(KEY_IMAGE_QUALITY))
    private val _videoQuality = MutableStateFlow(read(KEY_VIDEO_QUALITY))

    val imageQuality: StateFlow<QualityPreset> = _imageQuality.asStateFlow()
    val videoQuality: StateFlow<QualityPreset> = _videoQuality.asStateFlow()

    fun setImageQuality(quality: QualityPreset) {
        prefs.edit().putString(KEY_IMAGE_QUALITY, quality.name).apply()
        _imageQuality.value = quality
    }

    fun setVideoQuality(quality: QualityPreset) {
        prefs.edit().putString(KEY_VIDEO_QUALITY, quality.name).apply()
        _videoQuality.value = quality
    }

    private fun read(key: String): QualityPreset =
        runCatching { QualityPreset.valueOf(prefs.getString(key, QualityPreset.BALANCED.name)!!) }
            .getOrDefault(QualityPreset.BALANCED)

    private companion object {
        const val PREFS_NAME = "openconvert.prefs"
        const val KEY_IMAGE_QUALITY = "image_quality"
        const val KEY_VIDEO_QUALITY = "video_quality"
    }
}
