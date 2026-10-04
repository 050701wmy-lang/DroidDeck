package com.droiddeck.launcher

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/** This fork starts in Simplified Chinese regardless of the device's language. */
internal object AppLanguage {
    fun wrap(base: Context): Context {
        // Override only language; density, orientation and theme still follow the device.
        val override = Configuration().apply {
            setLocales(LocaleList(Locale.SIMPLIFIED_CHINESE))
        }
        return base.createConfigurationContext(override)
    }
}
