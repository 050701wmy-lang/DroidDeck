package com.droiddeck.launcher.input

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class BackQamInputTest {
    private val changes = mutableListOf<Boolean>()
    private val input = BackQamInput(changes::add)

    @Test fun aHoldAndItsRepeatsProduceOnePressAndOneRelease() {
        assertTrue(send(KeyEvent.ACTION_DOWN))
        for (repeat in 1..10) assertTrue(send(KeyEvent.ACTION_DOWN, repeat = repeat))
        assertEquals(listOf(true), changes)
        assertTrue(send(KeyEvent.ACTION_UP))
        assertEquals(listOf(true, false), changes)
    }

    @Test fun otherButtonsAndSelectPassThroughWhileBackIsHeld() {
        send(KeyEvent.ACTION_DOWN)
        for (code in listOf(KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_SELECT)) {
            for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                assertFalse(input.onKeyEvent(KeyEvent(action, code), true))
            }
        }
        assertEquals(listOf(true), changes)
        send(KeyEvent.ACTION_UP)
        assertEquals(listOf(true, false), changes)
    }

    @Test fun disabledRoutingPreservesBackNavigation() {
        assertFalse(send(KeyEvent.ACTION_DOWN, enabled = false))
        assertFalse(send(KeyEvent.ACTION_UP, enabled = false))
        assertTrue(changes.isEmpty())
    }

    @Test fun losingFocusReleasesQamAndConsumesTheLateRelease() {
        send(KeyEvent.ACTION_DOWN)
        input.cancel()
        input.cancel()
        assertTrue(send(KeyEvent.ACTION_DOWN, repeat = 1, enabled = false))
        assertTrue(send(KeyEvent.ACTION_UP, enabled = false))
        assertEquals(listOf(true, false), changes)
        assertFalse(send(KeyEvent.ACTION_UP, enabled = false))
    }

    @Test fun openingAnOverlayReleasesQamBeforeRoutingItsNextKey() {
        send(KeyEvent.ACTION_DOWN)
        assertFalse(input.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B), false))
        assertTrue(send(KeyEvent.ACTION_UP, enabled = false))
        assertEquals(listOf(true, false), changes)
    }

    @Test fun repeatsAfterCancellationCannotReassertQam() {
        send(KeyEvent.ACTION_DOWN)
        input.cancel()
        assertTrue(send(KeyEvent.ACTION_DOWN, repeat = 1))
        assertEquals(listOf(true, false), changes)
        assertTrue(send(KeyEvent.ACTION_DOWN))
        assertTrue(send(KeyEvent.ACTION_UP))
        assertEquals(listOf(true, false, true, false), changes)
    }

    @Test fun cancelledReleaseStillReleasesQam() {
        send(KeyEvent.ACTION_DOWN)
        assertTrue(input.onKeyEvent(KeyEvent(0, 100, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK,
            0, 0, 4, 0, KeyEvent.FLAG_CANCELED), true))
        assertEquals(listOf(true, false), changes)
    }

    @Test fun removingOneDeviceDoesNotReleaseAnotherDevicesHold() {
        send(KeyEvent.ACTION_DOWN, device = 4)
        send(KeyEvent.ACTION_DOWN, device = 5)
        input.onDeviceRemoved(4)
        assertTrue(changes.last())
        send(KeyEvent.ACTION_UP, device = 5)
        assertFalse(changes.last())
        send(KeyEvent.ACTION_DOWN, device = 4)
        input.onDeviceRemoved(4)
        assertFalse(changes.last())
    }

    private fun send(action: Int, repeat: Int = 0, device: Int = 4, enabled: Boolean = true) =
        input.onKeyEvent(KeyEvent(0, 100, action, KeyEvent.KEYCODE_BACK, repeat, 0, device, 0), enabled)
}
