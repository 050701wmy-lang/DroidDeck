package com.droiddeck.launcher.session

import com.droiddeck.launcher.wayland.CompositorHost
import com.droiddeck.launcher.wayland.WaylandCompositor
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, shadows = [FrameRateCompositorShadow::class])
class CompositorHostTest {
    @Test fun activeOutputAndLayerCadenceStayAlignedUnlessExplicitlyLimited() {
        val previous = SessionState.refreshHz
        try {
            CompositorHost.updateRefreshRate(165f, 0)
            assertEquals(165f, SessionState.refreshHz, 0f)
            assertEquals(165f, FrameRateCompositorShadow.output, 0f)
            assertEquals(165f, FrameRateCompositorShadow.layer, 0f)
            assertEquals(0, FrameRateCompositorShadow.limit)
            CompositorHost.updateRefreshRate(165f, 120)
            assertEquals(165f, FrameRateCompositorShadow.output, 0f)
            assertEquals(120f, FrameRateCompositorShadow.layer, 0f)
            assertEquals(120, FrameRateCompositorShadow.limit)
            CompositorHost.updateRefreshRate(Float.NaN, 0)
            assertEquals(120f, FrameRateCompositorShadow.layer, 0f)
            assertEquals(120, FrameRateCompositorShadow.limit)
        } finally {
            SessionState.refreshHz = previous
        }
    }
}

@Implements(WaylandCompositor::class)
class FrameRateCompositorShadow {
    companion object {
        var output = 0f
        var layer = 0f
        var limit = 0
        @JvmStatic @Implementation fun __staticInitializer__() {}
        @JvmStatic @Implementation fun nativeSetOutputRefreshRate(hz: Float) { output = hz }
        @JvmStatic @Implementation fun nativeSetLayerFrameRate(hz: Float) { layer = hz }
        @JvmStatic @Implementation fun nativeSetFpsLimit(fps: Int) { limit = fps }
    }
}
