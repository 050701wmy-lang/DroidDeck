package com.droiddeck.launcher.session

import com.droiddeck.launcher.SessionActivity
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SteamDownloadHintTest {
    @Test fun downloadFailureSurvivesGamescopeSuccessAndCleanupLines() {
        val activity = Robolectric.buildActivity(SessionActivity::class.java).get()
        val log = File.createTempFile("steam-download", ".log")
        try {
            log.writeText("== STEAM_DOWNLOAD_FAILED public_all 35\n[gamescope] Primary child shut down!\nBroken pipe\n")
            val method = SessionActivity::class.java.getDeclaredMethod("sessionEndHint", File::class.java)
                .apply { isAccessible = true }
            val hint = method.invoke(activity, log)!!
            val text = hint.javaClass.getDeclaredField("text").apply { isAccessible = true }.get(hint) as String
            assertTrue(text.contains("public_all"))
            assertTrue(text.contains("35"))
            assertTrue(hint.javaClass.getDeclaredField("evenOnSuccess").apply { isAccessible = true }.getBoolean(hint))
            log.writeText("[gamescope] Primary child shut down!\n")
            assertNull(method.invoke(activity, log))
        } finally {
            log.delete()
        }
    }
}
