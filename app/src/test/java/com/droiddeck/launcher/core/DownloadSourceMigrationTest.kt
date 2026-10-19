package com.droiddeck.launcher.core

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class DownloadSourceMigrationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val legacy get() = context.getSharedPreferences("github-downloads", Context.MODE_PRIVATE)
    private val current get() = context.getSharedPreferences("download-source", Context.MODE_PRIVATE)

    @Before fun clearSettings() {
        legacy.edit().clear().commit()
        current.edit().clear().commit()
    }

    @Test fun savedMirrorMigratesWithoutProxyingCatalogs() {
        legacy.edit().putString("prefix", "https://mirror.example/").commit()
        DownloadSource.init(context)
        val asset = "https://github.com/a/b/releases/download/v1/file.zip"
        val catalog = "https://raw.githubusercontent.com/Droid-Deck/DroidDeck-CI/main/catalog.json"
        assertEquals(DownloadSource.MODE_CUSTOM, DownloadSource.mode(context))
        assertEquals("https://mirror.example/$asset", DownloadSource.candidates(asset).first())
        assertEquals(listOf(catalog), DownloadSource.candidates(catalog))
    }

    @Test fun savedDirectChoiceMigrates() {
        legacy.edit().putString("prefix", "").commit()
        DownloadSource.init(context)
        assertEquals(DownloadSource.MODE_DIRECT, DownloadSource.mode(context))
    }

    @Test fun newSettingsAndFreshInstallDefaultsSurvive() {
        DownloadSource.init(context)
        assertEquals(DownloadSource.MODE_AUTO, DownloadSource.mode(context))
        DownloadSource.setMode(context, DownloadSource.MODE_DIRECT)
        legacy.edit().putString("prefix", "https://mirror.example/").commit()
        DownloadSource.init(context)
        assertEquals(DownloadSource.MODE_DIRECT, DownloadSource.mode(context))
    }
}
