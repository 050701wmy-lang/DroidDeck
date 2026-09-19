package com.steamdeck.launcher.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.steamdeck.launcher.R
import com.steamdeck.launcher.SessionActivity
import com.steamdeck.launcher.audio.PulseAudioComponent
import com.steamdeck.launcher.core.EnvVars
import com.steamdeck.launcher.core.EnvironmentComponent
import com.steamdeck.launcher.core.FileUtils
import com.steamdeck.launcher.core.ProcessHelper
import com.steamdeck.launcher.input.FakeInputWriter
import com.steamdeck.launcher.runtime.LinuxNetworkLinkComponent
import com.steamdeck.launcher.runtime.LinuxRuntime
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Owns the running session: the proot tree, the audio daemon, the network link and the locks that
 * keep all three alive while the app is not on screen.
 *
 * The session deliberately does **not** belong to the activity. Android demotes a process the
 * moment it loses its last visible activity, and the low-memory killer then reaps the guest — so
 * leaving Big Picture to answer a message would come back to a dead Steam. A foreground service
 * holds the process at perceptible priority, a partial wake lock keeps the CPU from dropping the
 * guest's threads, and a high-performance WiFi lock keeps the radio out of power-save so a
 * backgrounded download does not throttle to nothing. All three are Bannerlator's recipe, where
 * each was added to fix a failure seen on a device.
 *
 * The activity comes and goes on top of this; see [com.steamdeck.launcher.wayland.CompositorHost].
 */
class SessionService : Service() {
    private val components = ArrayList<EnvironmentComponent>()
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var sessionPid = -1

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "stop requested from the notification")
            stopSession(0)
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        if (SessionState.running) return START_NOT_STICKY
        SessionState.running = true
        SessionState.firstFrameSeen = false
        acquireLocks()
        Thread({ runSession() }, "session-start").start()
        // The activity or the notification stops us; the system must not resurrect a session whose
        // guest processes are long gone.
        return START_NOT_STICKY
    }

    // ── The session ─────────────────────────────────────────────────────────────────────────

    private fun runSession() {
        try {
            LinuxRuntime.writeAccounts(this)
        } catch (e: Exception) {
            Log.e(TAG, "could not write the guest's passwd/group", e)
            stopSession(-1)
            return
        }

        val root = LinuxRuntime.rootDir(this)
        val sessionRoot = LinuxRuntime.sessionRoot(this).apply { mkdirs() }
        val runtimeDir = File(filesDir, ".wayland-rt").apply { mkdirs() }
        killStragglers()
        SessionFiles.stage(this, root)

        val logDir = SessionFiles.logDirectory(this)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val sessionLog = File(logDir, "session-$stamp.log")
        SessionState.logFile = sessionLog

        val size = SessionState.outputSize
        val guest = ArrayList<String>()
        guest.add("/usr/bin/env")
        guest.add("-i")
        guest.add("HOME=/root")
        guest.add("USER=root")
        guest.add("PATH=/usr/local/bin:/usr/bin:/bin")
        guest.add("TERM=xterm-256color")
        guest.add("LANG=C.UTF-8")
        guest.add("XDG_RUNTIME_DIR=" + runtimeDir.path)
        guest.add("XDG_SESSION_TYPE=wayland")
        guest.add("WAYLAND_DISPLAY=wayland-0")
        guest.add("GAMESCOPE_FORCE_GENERAL_QUEUE=1")
        // Steam's CEF needs GL and the rootfs ships no native GL driver: route it through Zink.
        guest.add("MESA_LOADER_DRIVER_OVERRIDE=zink")
        guest.add("GALLIUM_DRIVER=zink")
        guest.add("LIBGL_KOPPER_DRI2=true")
        LinuxRuntime.vulkanIcd(this)?.let { guest.add("VK_ICD_FILENAMES=" + it.path) }

        val pulse = PulseAudioComponent(this)
        pulse.setContext(this)
        guest.add("PULSE_SERVER=unix:" + pulse.socket().absolutePath)
        components.add(pulse)

        guest.add("BL_WIDTH=" + size.first)
        guest.add("BL_HEIGHT=" + size.second)
        guest.add("BL_FPS=0")
        guest.add("BL_REFRESH=" + Math.round(SessionState.refreshHz))
        guest.add("BL_LOG=" + sessionLog.path)
        guest.add("BL_DEBUG_DIR=" + File(logDir, "session-$stamp").path)

        val fakeInputDir = File(sessionRoot, "dev/input").apply { mkdirs() }
        val controllersOn = !File(Environment.getExternalStorageDirectory(), NO_PAD_SWITCH).exists()
        if (controllersOn) {
            FakeInputWriter.prepareRingSlots(fakeInputDir, 4)
            guest.add("FAKE_EVDEV_DIR=" + fakeInputDir.path)
            val rings = FakeInputWriter.getRingEnv(fakeInputDir)
            if (!rings.isNullOrEmpty()) guest.add("FAKE_EVDEV_MEMFD_PATHS=$rings")
            // SDL and Steam key their mapping database on bus+vendor+product: only a known
            // identity gets the standard layout without the user configuring the pad by hand.
            guest.add("FAKE_EVDEV_IDENTITY=xbox360")
            guest.add("FAKE_EVDEV_VIBRATION=1")
            guest.add("FAKE_EVDEV_STEAM_VIRTUAL=1")
            guest.add("SDL_JOYSTICK_DISABLE_UDEV=1")
            guest.add("SDL_HIDAPI_JOYSTICK_DISABLE_UDEV=1")
            guest.add("SDL_JOYSTICK_HIDAPI=0")
            if (File(Environment.getExternalStorageDirectory(), PAD_LOG_SWITCH).exists()) {
                guest.add("FAKE_EVDEV_LOG=1")
            }
            SessionState.fakeInputDir = fakeInputDir
        }
        guest.add(LinuxRuntime.SESSION_SCRIPT)
        guest.add(LinuxRuntime.MODE_STEAM)

        // Android has no /dev/shm; the cache stands in for it and, unlike the real thing, keeps
        // whatever a session leaves behind. The client abandons tens of megabytes of streams a run.
        FileUtils.clear(File(cacheDir, "shm"))

        val binds = ArrayList<String>()
        if (controllersOn) binds.add(fakeInputDir.path + ":/dev/input")

        val command = LinuxRuntime.command(
            this, sessionRoot, runtimeDir, Environment.getExternalStorageDirectory(), binds, guest,
        )

        val hostEnv = EnvVars()
        hostEnv.put("PROOT_LOADER", LinuxRuntime.prootLoader(this).path)
        hostEnv.put("PROOT_TMP_DIR", cacheDir.path)
        // proot links against a libtalloc beside it, and Android's linker does not search an
        // executable's own directory: unnamed, the process dies before it starts and says so only
        // in `logcat -b crash`.
        val prootLibs = LinuxRuntime.prootLibraryPath(this)
        if (prootLibs.isNotEmpty()) hostEnv.put("LD_LIBRARY_PATH", prootLibs)

        val networkLink = LinuxNetworkLinkComponent(this, root)
        networkLink.setContext(this)
        networkLink.publish()
        components.add(networkLink)
        components.forEach { it.start() }

        val line = command.joinToString(" ") { it.replace(" ", "\\ ") }
        sessionPid = ProcessHelper.exec(line, hostEnv.toStringArray(), root, { status ->
            Log.i(TAG, "session ended: $status")
            stopSession(status ?: -1)
        }, null)
        Log.i(TAG, "session pid $sessionPid, log ${sessionLog.path}")
    }

    /**
     * proot's --kill-on-exit takes its tracees down, but a session that died from the inside
     * (the client asserting, Xwayland going) leaves gamescopereaper and the session script
     * behind, still holding the Wayland socket and the audio server the next session needs. They
     * are our uid, so they are ours to kill.
     */
    private fun killStragglers() {
        val me = android.os.Process.myPid()
        val procs = File("/proc").listFiles { f -> f.name.all { it.isDigit() } } ?: return
        var killed = 0
        for (proc in procs) {
            val pid = proc.name.toIntOrNull() ?: continue
            if (pid == me) continue
            val cmdline = try {
                File(proc, "cmdline").readBytes().toString(Charsets.UTF_8).replace('\u0000', ' ')
            } catch (e: Exception) {
                continue
            }
            if (STRAGGLERS.none { cmdline.contains(it) }) continue
            android.os.Process.killProcess(pid)
            killed++
        }
        if (killed > 0) Log.w(TAG, "killed $killed leftover process(es) of a previous session")
    }

    private fun stopSession(status: Int) {
        if (!SessionState.running) return
        SessionState.running = false
        // proot runs with --kill-on-exit, so killing it takes the whole guest tree with it.
        if (sessionPid != -1) {
            android.os.Process.killProcess(sessionPid)
            sessionPid = -1
        }
        components.reversed().forEach {
            try {
                it.stop()
            } catch (e: Exception) {
                Log.w(TAG, "stopping ${it.javaClass.simpleName}", e)
            }
        }
        components.clear()
        FakeInputWriter.releaseAllRingSlots()
        releaseLocks()
        SessionState.notifyEnded(status)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Swiped out of recents. The activity is destroyed without any of our teardown running, so the
     * guest would survive as an orphan holding the rootfs and the GPU. Treat the swipe as "quit".
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "task removed — ending the session")
        stopSession(0)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stopSession(0)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Keeping the process alive ───────────────────────────────────────────────────────────

    private fun acquireLocks() {
        try {
            val power = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SteamDeck:session")?.apply {
                setReferenceCounted(false)
                // Capped, so a crash on some path cannot pin the CPU awake for good. A session
                // longer than this re-acquires from the notification tap; nothing else needs it.
                acquire(12L * 60L * 60L * 1000L)
            }
            Log.i(TAG, "wake lock held=${wakeLock?.isHeld}")
        } catch (t: Throwable) {
            Log.w(TAG, "no wake lock (${t.message}) — the session may be killed in the background")
        }
        try {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION") // deprecated from API 29, still honoured; targetSdk is 28
            wifiLock = wifi?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "SteamDeck:session-wifi")
                ?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            Log.i(TAG, "wifi lock held=${wifiLock?.isHeld}")
        } catch (t: Throwable) {
            // A partial wake lock keeps the process alive but does not stop WiFi power-save from
            // throttling a backgrounded download to nothing, which is what this lock is for.
            Log.w(TAG, "no wifi lock (${t.message}) — a backgrounded download may stall")
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (t: Throwable) {
            Log.w(TAG, "releasing the wake lock", t)
        }
        wakeLock = null
        try {
            wifiLock?.takeIf { it.isHeld }?.release()
        } catch (t: Throwable) {
            Log.w(TAG, "releasing the wifi lock", t)
        }
        wifiLock = null
    }

    // ── Notification ────────────────────────────────────────────────────────────────────────

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        // IMPORTANCE_LOW: it must never make a sound or push a heads-up over a game.
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.session_channel),
            NotificationManager.IMPORTANCE_LOW).apply {
            description = getString(R.string.session_channel_description)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        manager?.createNotificationChannel(channel)

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, SessionActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, SessionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_session)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.session_notification))
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, getString(R.string.stop_session), stop).build())
            .setOngoing(true)
            .setShowWhen(false)
            .apply { if (Build.VERSION.SDK_INT >= 31) setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE) }
            .build()
    }

    companion object {
        private const val TAG = "SessionService"
        private const val CHANNEL_ID = "session"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.steamdeck.launcher.STOP_SESSION"
        /** Command lines that can only belong to a session of ours. */
        private val STRAGGLERS = listOf("bannerlator-session", "gamescope", "Xwayland", "steamrtarm64",
            "steamwebhelper", "linuxfs/opt/android-host/proot", "pulseaudio/libpulseaudio.so")
        private const val NO_PAD_SWITCH = "Download/steamdeck-no-pad"
        private const val PAD_LOG_SWITCH = "Download/steamdeck-pad-log"

        fun start(context: Context) {
            val intent = Intent(context, SessionService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, SessionService::class.java).setAction(ACTION_STOP))
        }
    }
}
