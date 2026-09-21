package com.steamdeck.launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.steamdeck.launcher.core.FileUtils
import com.steamdeck.launcher.gpu.FrameGen
import com.steamdeck.launcher.gpu.LinuxVulkanDriver
import com.steamdeck.launcher.gpu.LinuxVulkanDriverManager
import com.steamdeck.launcher.gpu.TurnipDriver
import com.steamdeck.launcher.gpu.LsfgNative
import com.steamdeck.launcher.runtime.LinuxRuntime
import com.steamdeck.launcher.runtime.DesktopCatalog
import com.steamdeck.launcher.runtime.LinuxRuntimeInstaller
import com.steamdeck.launcher.session.SessionService
import com.steamdeck.launcher.ui.DesktopAppsDialog
import com.steamdeck.launcher.ui.PackageRow
import com.steamdeck.launcher.session.OfflineMode
import com.steamdeck.launcher.session.ProtonExtras
import com.steamdeck.launcher.session.SessionPrefs
import com.steamdeck.launcher.ui.ProtonDialog
import com.steamdeck.launcher.ui.ProtonRow
import com.steamdeck.launcher.core.CpuCores
import com.steamdeck.launcher.ui.AudioDialog
import com.steamdeck.launcher.ui.CoreRow
import com.steamdeck.launcher.ui.PerformanceDialog
import com.steamdeck.launcher.ui.DriverDialog
import com.steamdeck.launcher.ui.DriverRow
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
    private var showProtons by mutableStateOf(false)
    private var showApps by mutableStateOf(false)
    private var catalog by mutableStateOf<List<DesktopCatalog.Entry>?>(emptyList())
    private var packageRows by mutableStateOf<List<PackageRow>?>(emptyList())
    private var pkgStage by mutableStateOf<String?>(null)
    private var pkgPercent by mutableIntStateOf(-1)
    private var desktopInstalled by mutableStateOf(false)
    private var offlineAccount by mutableStateOf<String?>(null)
    private var offline by mutableStateOf(false)
    private var protonRows by mutableStateOf<List<ProtonRow>>(emptyList())
    private var showDrivers by mutableStateOf(false)
    private var showAudio by mutableStateOf(false)
    private var showPerformance by mutableStateOf(false)
    private var clientOverride by mutableStateOf(false)
    private var clientCores by mutableStateOf<Set<Int>>(emptySet())
    private var gameCores by mutableStateOf<Set<Int>>(emptySet())
    private var tuSysmem by mutableStateOf(false)
    private var zinkLazy by mutableStateOf(false)
    private var directAudio by mutableStateOf(false)
    private var mic by mutableStateOf(false)
    private var linuxRows by mutableStateOf<List<DriverRow>>(emptyList())
    private var linuxSteam by mutableStateOf("")
    private var linuxDesktop by mutableStateOf("")
    private var androidRows by mutableStateOf<List<DriverRow>>(emptyList())
    private var androidSelected by mutableStateOf("")

    // The system file picker, once per driver list: the two lists validate differently, and the
    // reason a zip is refused names the list it belongs in.
    private val pickLinuxDriver = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importDriver(uri, linux = true)
    }
    private val pickAndroidDriver = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importDriver(uri, linux = false)
    }

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
                        desktopInstalled = desktopInstalled,
                        offlineAccount = offlineAccount, offline = offline,
                    ),
                    onPlay = { startActivity(Intent(this, SessionActivity::class.java)) },
                    onDesktop = {
                        startActivity(Intent(this, SessionActivity::class.java)
                            .putExtra(SessionService.EXTRA_MODE, SessionService.MODE_DESKTOP))
                    },
                    onApps = { openApps() },
                    onOffline = {
                        OfflineMode.setEnabled(this, !OfflineMode.enabled(this))
                        offline = OfflineMode.enabled(this)
                    },
                    onRuntime = { onRuntimeButton() },
                    onFrameGen = { showFrameGen = true },
                    onProtons = { refreshProtons(); showProtons = true },
                    onDrivers = { refreshDrivers(); showDrivers = true },
                    onAudio = { directAudio = SessionPrefs.directAudio(this); mic = SessionPrefs.micEnabled(this); showAudio = true },
                    onPerformance = { refreshCores(); showPerformance = true },
                    onCredits = { showCredits = true },
                )
                if (showApps) DesktopAppsDialog(
                    rows = packageRows, busyStage = pkgStage, busyPercent = pkgPercent,
                    onInstall = { id -> installPackage(id) },
                    onRemove = { id -> catalog?.firstOrNull { it.id == id }?.let { DesktopCatalog.remove(this, it) }; refreshPackages() },
                    onDismiss = { showApps = false },
                )
                if (showProtons) ProtonDialog(
                    rows = protonRows,
                    onInstall = { id -> ProtonExtras.tools.first { it.id == id }.let { ProtonExtras.queue(this, it) }; refreshProtons() },
                    onCancel = { id -> ProtonExtras.tools.first { it.id == id }.let { ProtonExtras.unqueue(this, it) }; refreshProtons() },
                    onRemove = { id -> ProtonExtras.tools.first { it.id == id }.let { ProtonExtras.remove(this, it) }; refreshProtons() },
                    onDismiss = { showProtons = false },
                )
                if (showDrivers) DriverDialog(
                    linuxRows = linuxRows, linuxSteam = linuxSteam, linuxDesktop = linuxDesktop,
                    androidRows = androidRows, androidSelected = androidSelected,
                    onSelectLinux = { mode, id -> SessionPrefs.setLinuxDriver(this, mode, id); refreshDrivers() },
                    onImportLinux = { pickLinuxDriver.launch(ZIP_TYPES) },
                    onRemoveLinux = { id -> LinuxVulkanDriverManager(this).removeDriver(id); refreshDrivers() },
                    onSelectAndroid = { id -> SessionPrefs.setAndroidDriver(this, id); refreshDrivers() },
                    onImportAndroid = { pickAndroidDriver.launch(ZIP_TYPES) },
                    onRemoveAndroid = { id -> TurnipDriver(this).remove(id); refreshDrivers() },
                    onDismiss = { showDrivers = false },
                )
                if (showAudio) AudioDialog(
                    directAudio = directAudio, mic = mic,
                    onDirectAudio = { on -> SessionPrefs.setDirectAudio(this, on); directAudio = on },
                    onMic = { on ->
                        SessionPrefs.setMicEnabled(this, on)
                        mic = on
                        // The session checks the grant itself at start; asking here means the
                        // answer is in before the first session that wants it.
                        if (on && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 2)
                        }
                    },
                    onDismiss = { showAudio = false },
                )
                if (showPerformance) PerformanceDialog(
                    cores = CpuCores.all.map { c -> CoreRow(c, "cpu$c" + (CpuCores.maxGhz(c)?.let { String.format(java.util.Locale.US, " · %.1f GHz", it) } ?: "")) },
                    clientOverride = clientOverride, clientCores = clientCores, gameCores = gameCores,
                    tuSysmem = tuSysmem, zinkLazy = zinkLazy,
                    onClientOverride = { on -> SessionPrefs.setClientCpusOverride(this, on); clientOverride = on },
                    onTuSysmem = { on -> SessionPrefs.setTuSysmem(this, on); tuSysmem = on },
                    onZinkLazy = { on -> SessionPrefs.setZinkLazy(this, on); zinkLazy = on },
                    onClientCore = { core, on ->
                        clientCores = if (on) clientCores + core else clientCores - core
                        SessionPrefs.setClientCpus(this, CpuCores.format(clientCores))
                    },
                    onGameCore = { core, on ->
                        gameCores = if (on) gameCores + core else gameCores - core
                        SessionPrefs.setGameCpus(this, CpuCores.format(gameCores))
                    },
                    onDismiss = { showPerformance = false },
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

    private fun openApps() {
        showApps = true
        if (catalog.isNullOrEmpty()) Thread({
            val fetched = DesktopCatalog.fetch()
            ui.post { catalog = fetched; refreshPackages() }
        }, "catalog-desktop").start()
        else refreshPackages()
    }

    private fun refreshPackages() {
        packageRows = catalog?.sortedBy { it.tier }?.map {
            PackageRow(it.id, it.name, it.tier, it.version, FileUtils.sizeToString(it.size), it.notes,
                DesktopCatalog.installed(this, it.id))
        }
        desktopInstalled = DesktopCatalog.desktopInstalled(this)
    }

    private fun installPackage(id: String) {
        val entry = catalog?.firstOrNull { it.id == id } ?: return
        if (pkgStage != null) return
        pkgStage = "Starting…"; pkgPercent = -1
        Thread({
            val problem = DesktopCatalog.install(this, entry) { stage, percent ->
                ui.post { pkgStage = stage; pkgPercent = percent }
            }
            ui.post {
                pkgStage = null
                if (problem != null) android.widget.Toast.makeText(this, "${entry.name}: $problem", android.widget.Toast.LENGTH_LONG).show()
                refreshPackages()
            }
        }, "install-pkg").start()
    }

    /** Both driver lists as the dialog shows them, re-read from disk so an import or removal shows at once. */
    private fun refreshDrivers() {
        val lm = LinuxVulkanDriverManager(this)
        linuxRows = LinuxVulkanDriver.optionValues(this).map { id ->
            if (id.isEmpty()) DriverRow("", "Runtime default", "the Turnip built into the runtime", false)
            else DriverRow(
                id, lm.getDriverName(id),
                listOfNotNull(
                    lm.getDriverVersion(id).takeIf { it.isNotEmpty() },
                    lm.getMinGlibc(id).takeIf { it.isNotEmpty() }?.let { "glibc $it+" },
                ).joinToString(" · ").ifEmpty { "imported" },
                true,
            )
        }
        linuxSteam = SessionPrefs.linuxDriver(this, SessionService.MODE_STEAM)
        linuxDesktop = SessionPrefs.linuxDriver(this, SessionService.MODE_DESKTOP)
        val td = TurnipDriver(this)
        val auto = td.autoId()
        androidRows = buildList {
            add(DriverRow(
                TurnipDriver.AUTO, "Auto — picked by GPU",
                if (auto == "system") "system Vulkan: no bundled build for this GPU" else "${td.displayName(auto)} (bundled)",
                false,
            ))
            for (id in TurnipDriver.BUNDLED) add(DriverRow(id, td.displayName(id), "bundled" + td.driverVersion(id).let { if (it.isEmpty()) "" else " · $it" }, false))
            for (id in td.enumerateImported()) add(DriverRow(id, td.displayName(id), "imported" + td.driverVersion(id).let { if (it.isEmpty()) "" else " · $it" }, true))
        }
        androidSelected = SessionPrefs.androidDriver(this)
    }

    /**
     * Import off the main thread — a driver zip is a few MB and the glibc check reads the whole
     * library — then say what happened. A refusal's message is the user-facing reason.
     */
    private fun importDriver(uri: Uri, linux: Boolean) {
        val name = displayNameOf(uri)
        Thread({
            val problem = try {
                if (linux) LinuxVulkanDriverManager(this).installDriver(uri, name)
                else TurnipDriver(this).installFromZip(uri, name)
                null
            } catch (e: IllegalArgumentException) {
                e.message
            } catch (e: Exception) {
                Log.w(TAG, "driver import", e)
                "Import failed: ${e.message}"
            }
            ui.post {
                android.widget.Toast.makeText(
                    this, problem ?: "Imported ${name ?: "driver"}",
                    if (problem != null) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT,
                ).show()
                refreshDrivers()
            }
        }, "import-driver").start()
    }

    private fun displayNameOf(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) {
        null
    }

    /** The two masks as the dialog shows them; an empty stored list shows as every core ticked. */
    private fun refreshCores() {
        clientOverride = SessionPrefs.clientCpusOverride(this)
        clientCores = CpuCores.parse(SessionPrefs.clientCpus(this)).ifEmpty { CpuCores.all.toSet() }
        gameCores = CpuCores.parse(SessionPrefs.gameCpus(this)).ifEmpty { CpuCores.all.toSet() }
        tuSysmem = SessionPrefs.tuSysmem(this)
        zinkLazy = SessionPrefs.zinkLazy(this)
    }

    private fun refreshProtons() {
        protonRows = ProtonExtras.tools.map { ProtonRow(it.id, it.name, ProtonExtras.installed(this, it), ProtonExtras.queued(this, it)) }
    }

    private fun refresh() {
        desktopInstalled = DesktopCatalog.desktopInstalled(this)
        offlineAccount = OfflineMode.account(this)
        offline = OfflineMode.enabled(this)
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
        /** What the picker offers for a driver zip; some file apps label a zip as a plain stream. */
        private val ZIP_TYPES = arrayOf("application/zip", "application/octet-stream")
    }
}
