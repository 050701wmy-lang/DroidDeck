package com.droiddeck.launcher

import com.droiddeck.launcher.core.AppLanguage
import android.app.Application
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class,
    qualifiers = "en-rUS-land-hdpi-night")
class AppLanguageTest {
    @Test fun englishDeviceGetsChineseWithoutChangingItsDisplayConfiguration() {
        val base = RuntimeEnvironment.getApplication<Application>()
        base.getSharedPreferences("language", 0).edit().clear().commit()
        val localized = AppLanguage.wrap(base)
        assertEquals(Locale.SIMPLIFIED_CHINESE, localized.resources.configuration.locales[0])
        assertEquals("取消", localized.getString(android.R.string.cancel))
        assertEquals(Locale.US, base.resources.configuration.locales[0])
        assertEquals(base.resources.configuration.orientation, localized.resources.configuration.orientation)
        assertEquals(base.resources.configuration.densityDpi, localized.resources.configuration.densityDpi)
        assertEquals(base.resources.configuration.uiMode, localized.resources.configuration.uiMode)
    }

    @Test fun recreatedContextRemainsChineseAfterSystemLanguageChanges() {
        val base = RuntimeEnvironment.getApplication<Application>()
        val changed = base.createConfigurationContext(Configuration().apply {
            setLocales(LocaleList(Locale.forLanguageTag("es-ES")))
        })
        base.getSharedPreferences("language", 0).edit().clear().commit()
        val localized = AppLanguage.wrap(changed)
        assertEquals("取消", localized.getString(android.R.string.cancel))
        assertEquals(Locale.SIMPLIFIED_CHINESE, localized.resources.configuration.locales[0])
    }
}
