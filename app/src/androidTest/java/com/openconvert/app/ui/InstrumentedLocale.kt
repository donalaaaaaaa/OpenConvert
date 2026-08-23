package com.openconvert.app.ui

import android.content.Context
import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale

/** Hosted AVDs are en-US. Assertions against the zh catalog need this context. */
object InstrumentedLocale {
    fun zhContext(): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.SIMPLIFIED_CHINESE)
        return base.createConfigurationContext(config)
    }
}
