package com.droiddeck.launcher.stores.itch

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.droiddeck.launcher.R
import com.droiddeck.launcher.stores.CatalogItem
import com.droiddeck.launcher.stores.InstalledStoreGame
import com.droiddeck.launcher.stores.Store
import com.droiddeck.launcher.stores.StoresState
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

internal class ItchUpdate(val uploadId: String, val updatedAt: String, val filename: String, val replaced: Boolean)

internal object ItchUpdates {
    private const val KEY_AT = "updates_checked_at"
    private const val KEY_FOUND = "updates_found"
    private const val THROTTLE_MS = 6 * 60 * 60 * 1000L
    private const val GAP_MS = 1_000L

    val available = mutableStateMapOf<String, ItchUpdate>()
    var checking by mutableStateOf(false)
        private set
    var done by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(0)
        private set

    private val running = AtomicBoolean(false)
    private val restored = AtomicBoolean(false)

    internal fun evaluate(uploads: JSONArray?, uploadId: String, version: String): ItchUpdate? {
        if (uploads == null || version.isBlank()) return null
        val current = ItchUploads.byId(uploads, uploadId)
        if (current != null) {
            val stamp = ItchUploads.updatedAt(current)
            return if (stamp > version) ItchUpdate(uploadId, stamp, ItchUploads.filename(current), false) else null
        }
        val next = ItchUploads.windows(uploads) ?: return null
        val stamp = ItchUploads.updatedAt(next)
        if (uploadId.isBlank() && stamp <= version) return null
        return ItchUpdate(next.optString("id"), stamp, ItchUploads.filename(next), uploadId.isNotBlank())
    }

    internal fun encode(found: Map<String, ItchUpdate>): String = JSONObject().also { out ->
        for ((key, u) in found) out.put(key, JSONObject().put("uploadId", u.uploadId).put("updatedAt", u.updatedAt).put("filename", u.filename).put("replaced", u.replaced))
    }.toString()

    internal fun decode(raw: String?): Map<String, ItchUpdate> {
        val json = runCatching { JSONObject(raw.orEmpty()) }.getOrNull() ?: return emptyMap()
        val out = LinkedHashMap<String, ItchUpdate>()
        for (key in json.keys()) {
            val u = json.optJSONObject(key) ?: continue
            val id = u.optString("uploadId").ifBlank { continue }
            out[key] = ItchUpdate(id, u.optString("updatedAt"), u.optString("filename"), u.optBoolean("replaced"))
        }
        return out
    }

    private fun restore(app: Context) {
        if (!restored.compareAndSet(false, true)) return
        val saved = decode(ItchPrefs.get(app).getString(KEY_FOUND, null))
        StoresState.post { for ((k, v) in saved) if (k !in available) available[k] = v }
    }

    private fun save(app: Context) {
        ItchPrefs.get(app).edit().putString(KEY_FOUND, encode(available.toMap())).apply()
    }

    fun check(context: Context, force: Boolean) {
        val app = context.applicationContext
        if (!running.compareAndSet(false, true)) return
        Thread({
            try {
                restore(app)
                val prefs = ItchPrefs.get(app)
                if (!force && System.currentTimeMillis() - prefs.getLong(KEY_AT, 0L) < THROTTLE_MS) return@Thread
                val key = ItchAuth.key(app)
                if (key.isBlank()) return@Thread
                val games = StoresState.installed.toList().filter { it.sidecar.store == Store.ITCH && it.sidecar.isInstalled }
                StoresState.post { total = games.size; done = 0; checking = true }
                val found = LinkedHashMap<String, ItchUpdate>()
                val answered = HashSet<String>()
                var failed = false
                games.forEachIndexed { i, game ->
                    if (i > 0) Thread.sleep(GAP_MS)
                    val extra = game.sidecar.extra
                    val gameId = extra["gameId"].orEmpty()
                    if (gameId.isNotEmpty()) {
                        val uploads = ItchApi.uploads(key, gameId, extra["downloadKeyId"].orEmpty())
                        if (uploads == null) failed = true
                        else {
                            answered += game.key
                            evaluate(uploads, extra["uploadId"].orEmpty(), game.sidecar.installVersion)?.let { found[game.key] = it }
                        }
                    }
                    StoresState.post { done = i + 1 }
                }
                StoresState.post {
                    for (k in answered) {
                        val u = found[k]
                        if (u != null) available[k] = u else available.remove(k)
                    }
                    save(app)
                }
                if (!failed) prefs.edit().putLong(KEY_AT, System.currentTimeMillis()).apply()
                StoresState.logLine("itch.io: checked ${games.size} installed games, ${found.size} with updates")
                if (force) StoresState.post {
                    val text = if (found.isNotEmpty()) app.getString(R.string.stores_itch_updates_found, found.size) else app.getString(R.string.upd_current_status)
                    Toast.makeText(app, text, Toast.LENGTH_SHORT).show()
                }
            } finally {
                running.set(false)
                StoresState.post { checking = false }
            }
        }, "itch-updates").start()
    }

    fun start(context: Context, game: InstalledStoreGame, update: ItchUpdate) {
        val app = context.applicationContext
        val root = game.folder.parentFile?.parentFile ?: return
        val extra = game.sidecar.extra + ("uploadId" to update.uploadId)
        val item = CatalogItem(
            Store.ITCH, game.sidecar.id, game.sidecar.title, game.sidecar.hero, tallImageUrl = game.sidecar.cover,
            isFree = extra["downloadKeyId"].isNullOrBlank(), owned = true, extra = extra,
        )
        StoresState.install(app, item, root)
    }

    fun applied(context: Context, key: String) {
        val app = context.applicationContext
        StoresState.post {
            available.remove(key)
            save(app)
        }
    }

    fun clear(context: Context) {
        val app = context.applicationContext
        ItchPrefs.get(app).edit().remove(KEY_AT).remove(KEY_FOUND).apply()
        StoresState.post { available.clear() }
    }
}
