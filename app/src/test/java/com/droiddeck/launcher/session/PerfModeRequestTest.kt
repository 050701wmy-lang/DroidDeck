package com.droiddeck.launcher.session

import android.app.Activity
import android.provider.Settings
import android.view.Display
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplayManager
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PerfModeRequestTest {
    @Test fun refreshSelectionRespectsPeakAndFrameCadence() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            @Suppress("DEPRECATION") val display = activity.windowManager.defaultDisplay
            val current = display.mode
            fun mode(id: Int, hz: Float) = ReflectionHelpers.callConstructor(Display.Mode::class.java,
                ClassParameter.from(Int::class.javaPrimitiveType, id),
                ClassParameter.from(Int::class.javaPrimitiveType, current.physicalWidth),
                ClassParameter.from(Int::class.javaPrimitiveType, current.physicalHeight),
                ClassParameter.from(Float::class.javaPrimitiveType, hz))
            val lowerId = current.modeId + 1
            ShadowDisplayManager.setSupportedModes(display.displayId,
                mode(current.modeId, 165f), mode(lowerId, 120f), mode(lowerId + 1, 90f))
            Settings.System.putFloat(activity.contentResolver, "peak_refresh_rate", 120f)
            PerfMode.apply(activity)
            assertEquals(lowerId, activity.window.attributes.preferredDisplayModeId)

            Settings.System.putFloat(activity.contentResolver, "peak_refresh_rate", 165f)
            PerfMode.apply(activity, 60)
            assertEquals(lowerId, activity.window.attributes.preferredDisplayModeId)
            PerfMode.apply(activity)
            assertEquals(current.modeId, activity.window.attributes.preferredDisplayModeId)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
