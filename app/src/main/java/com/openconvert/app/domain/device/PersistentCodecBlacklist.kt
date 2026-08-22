package com.openconvert.app.domain.device

import android.content.Context

class PersistentCodecBlacklist(
    context: Context,
) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val inner = CodecBlacklist(
        prefs.getStringSet(KEY, emptySet()).orEmpty().toMutableSet(),
    )

    fun record(manufacturer: String, model: String, codecName: String) {
        inner.record(manufacturer, model, codecName)
        prefs.edit().putStringSet(KEY, inner.entries()).apply()
    }

    fun shouldSkipHardware(
        manufacturer: String,
        model: String,
        codecName: String = CodecBlacklist.ANY_CODEC,
    ): Boolean = inner.shouldSkipHardware(manufacturer, model, codecName)

    companion object {
        private const val PREFS = "openconvert-codec-blacklist"
        private const val KEY = "entries"

        @Volatile
        private var instance: PersistentCodecBlacklist? = null

        fun get(context: Context): PersistentCodecBlacklist {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: PersistentCodecBlacklist(context).also { instance = it }
            }
        }
    }
}
