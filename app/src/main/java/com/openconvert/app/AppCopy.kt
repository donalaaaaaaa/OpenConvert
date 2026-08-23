package com.openconvert.app

import android.content.Context
import androidx.annotation.StringRes

/**
 * Process-wide string lookup so domain code (errors, notifications, task cards)
 * follows the system locale. Unbound on JVM unit tests — callers keep a Chinese
 * fallback so those tests stay hermetic.
 */
object AppCopy {
    @Volatile
    private var appContext: Context? = null

    fun bind(context: Context) {
        appContext = context.applicationContext
    }

    fun getOr(@StringRes id: Int, fallback: String): String {
        val ctx = appContext ?: return fallback
        return ctx.getString(id)
    }

    fun getOr(@StringRes id: Int, fallback: String, vararg formatArgs: Any): String {
        val ctx = appContext ?: return fallback
        return ctx.getString(id, *formatArgs)
    }
}
