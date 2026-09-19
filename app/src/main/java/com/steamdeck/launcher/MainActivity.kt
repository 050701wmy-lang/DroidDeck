package com.steamdeck.launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.steamdeck.launcher.core.FileUtils
import com.steamdeck.launcher.gpu.FrameGen
import com.steamdeck.launcher.gpu.LsfgNative
import com.steamdeck.launcher.runtime.LinuxRuntime
import com.steamdeck.launcher.runtime.LinuxRuntimeInstaller
import com.steamdeck.launcher.ui.ConfirmDialog
import com.steamdeck.launcher.ui.CreditsDialog
import com.steamdeck.launcher.ui.FrameGenDialog
import com.steamdeck.launcher.ui.MainScreen
import com.steamdeck.launcher.ui.MainUiState
import com.steamdeck.launcher.ui.SteamDeckTheme

/**
 * The whole app outside a session: is the runtime installed, is there a newer one, frame
 * generation, and one button that starts Steam. Everything a Steam client can do — the library,
 * the store, downloads, settings — is the client's own job once [SessionActivity] has it on screen.
 */
class MainActivity : ComponentActivity() {
    private val ui = Handler(Looper.getMainLooper())

    // The screen's state. Compose redraws whatever reads these when they change.
    private var installed by mutableStateOf<String?>(null)
    private var ready by mutableStateOf(false)
    private var available by mutableStateOf<LinuxRuntimeInstaller.Release?>(null)
    private var busy by mutableStateOf(false)
    private var stage by mutableStateOf("")
    private var percent by mutableIntStateOf(-1)
    private var failed by mutableStateOf(false)
    private var frameGenLabel by mutableStateOf("Off")
    private var showRemove by mutableStateOf(false)
    private var showFrameGen by mutableStateOf(false)
    private var showCredits by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SteamDeckTheme {
                MainScreen(
                    state = MainUiState(
                        installed = installed, ready = ready,
                        available = available?.version,
                        availableSize = available?.let { FileUtils.sizeToString(it.size) },
                        busy = busy, stage = stage, percent = percent, failed = failed,
                        frameGenLabel = frameGenLabel,
                    ),
                    onPlay = { startActivity(Intent(this, SessionActivity::class.java)) },
                    onRuntime = { onRuntimeButton() },
                    onFrameGen = { showFrameGen = true },
                    onCredits = { showCredits = true },
                )
                if (showRemove) ConfirmDialog(
                    title = "Remove Linux runtime",
                    text = "This deletes the runtime, the Steam client inside it, and every game installed there.",
                    confirm = "Remove",
                    onConfirm = { Thread({ LinuxRuntimeInstaller.uninstall(this); ui.post { refresh() } }, "uninstall").start() },
                    onDismiss = { showRemove = false },
                )
                if (showFrameGen) FrameGenDialog(
                    engine = FrameGen.engine(this), multiplier = FrameGen.multiplier(this),
                    lsfgReady = LsfgNative.isInstalled(this),
                    onPick = { engine, multiplier ->
                        FrameGen.set(this, engine, multiplier)
                        frameGenLabel = FrameGen.label(this)
                        showFrameGen = false
                    },
                    onDismiss = { showFrameGen = false },
                )
                if (showCredits) CreditsDialog { showCredits = false }
            }
        }

        // The session's logs land in Downloads so a failed run can be handed over as a folder
        // rather than dug out of app-private storage. targetSdk 28 means the old permission still
        // grants exactly that.
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        if (!busy) Thread({ checkCatalog() }, "catalog").start()
    }

    private fun refresh() {
        installed = LinuxRuntimeInstaller.installedVersion(this)
        ready = LinuxRuntime.isInstalled(this)
        frameGenLabel = FrameGen.label(this)
    }

    private fun onRuntimeButton() {
        if (busy) return
        val release = available
        if (installed != null && release?.version == installed) {
            // Nothing to install: offer the one destructive thing this screen can do.
            showRemove = true
            return
        }
        if (release == null) {
            Thread({ checkCatalog() }, "catalog").start()
            return
        }
        install(release)
    }

    private fun install(release: LinuxRuntimeInstaller.Release) {
        busy = true
        failed = false
        stage = "Starting…"
        percent = -1
        Thread({
            val ok = LinuxRuntimeInstaller.install(this, release) { s, p ->
                ui.post { stage = s; percent = p }
            }
            ui.post {
                busy = false
                failed = !ok
                refresh()
            }
        }, "install").start()
    }

    private fun checkCatalog() {
        val release = LinuxRuntimeInstaller.fetchRelease()
        Log.i(TAG, "catalog: " + (release?.version ?: "unreachable"))
        ui.post { if (release != null) available = release }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
