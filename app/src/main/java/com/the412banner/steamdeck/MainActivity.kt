package com.the412banner.steamdeck

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import com.the412banner.steamdeck.core.FileUtils
import com.the412banner.steamdeck.runtime.LinuxRuntime
import com.the412banner.steamdeck.runtime.LinuxRuntimeInstaller

/**
 * The whole app outside a session: is the runtime installed, is there a newer one, and one button
 * that starts Steam. Everything a Steam client can do — the library, the store, downloads,
 * settings — is the client's own job once {@link SessionActivity} has it on screen.
 */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var detail: TextView
    private lateinit var progress: ProgressBar
    private lateinit var playButton: Button
    private lateinit var runtimeButton: Button

    private val ui = Handler(Looper.getMainLooper())
    @Volatile private var busy = false
    @Volatile private var available: LinuxRuntimeInstaller.Release? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        detail = findViewById(R.id.detail)
        progress = findViewById(R.id.progress)
        playButton = findViewById(R.id.play)
        runtimeButton = findViewById(R.id.runtime)

        playButton.setOnClickListener { startActivity(Intent(this, SessionActivity::class.java)) }
        runtimeButton.setOnClickListener { onRuntimeButton() }

        // The session's logs land in Downloads so a failed run can be handed over as a folder
        // rather than dug out of app-private storage. targetSdk 28 means the old permission still
        // grants exactly that.
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        render()
        if (!busy) Thread({ checkCatalog() }, "catalog").start()
    }

    private fun onRuntimeButton() {
        if (busy) return
        val installed = LinuxRuntimeInstaller.installedVersion(this)
        if (installed != null && available?.version == installed) {
            // Nothing to install: offer the one destructive thing this screen can do.
            AlertDialog.Builder(this)
                .setTitle(R.string.remove_runtime)
                .setMessage(R.string.remove_runtime_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.remove) { _, _ ->
                    Thread({
                        LinuxRuntimeInstaller.uninstall(this)
                        ui.post { render() }
                    }, "uninstall").start()
                }
                .show()
            return
        }
        val release = available
        if (release == null) {
            Thread({ checkCatalog() }, "catalog").start()
            return
        }
        install(release)
    }

    private fun install(release: LinuxRuntimeInstaller.Release) {
        busy = true
        render()
        Thread({
            val ok = LinuxRuntimeInstaller.install(this, release) { stage, percent ->
                ui.post {
                    progress.isIndeterminate = percent < 0
                    if (percent >= 0) progress.progress = percent
                    detail.text = if (percent >= 0) "$stage $percent%" else stage
                }
            }
            busy = false
            ui.post {
                if (!ok) detail.text = getString(R.string.install_failed)
                render()
            }
        }, "install").start()
    }

    private fun checkCatalog() {
        val release = LinuxRuntimeInstaller.fetchRelease()
        if (release != null) available = release
        Log.i(TAG, "catalog: " + (release?.version ?: "unreachable"))
        ui.post { render() }
    }

    private fun render() {
        val installed = LinuxRuntimeInstaller.installedVersion(this)
        val ready = LinuxRuntime.isInstalled(this)
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        playButton.isEnabled = ready && !busy
        runtimeButton.isEnabled = !busy

        val offered = available
        when {
            busy -> {
                status.setText(R.string.working)
                runtimeButton.setText(R.string.working)
            }
            !ready -> {
                status.setText(R.string.runtime_missing)
                runtimeButton.setText(R.string.install_runtime)
                detail.text = if (offered != null) {
                    getString(R.string.runtime_download_size,
                        offered.version, FileUtils.sizeToString(offered.size))
                } else {
                    getString(R.string.catalog_unreachable)
                }
            }
            offered != null && offered.version != installed -> {
                status.setText(R.string.ready)
                runtimeButton.setText(R.string.update_runtime)
                detail.text = getString(R.string.runtime_update, installed ?: "?", offered.version)
            }
            else -> {
                status.setText(R.string.ready)
                runtimeButton.setText(R.string.remove_runtime)
                detail.text = getString(R.string.runtime_installed, installed ?: "?")
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
