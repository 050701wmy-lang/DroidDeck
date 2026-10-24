package com.droiddeck.launcher.stores.itch

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.droiddeck.launcher.stores.CatalogItem
import com.droiddeck.launcher.stores.StoresState
import org.json.JSONArray
import java.io.File
import java.security.MessageDigest

/** What the Store tab is showing: an order, a price filter and, later, platforms and tags. The default is the front page. */
internal data class BrowseQuery(
    val sort: String = ItchBrowse.SORT_POPULAR,
    val price: String = ItchBrowse.PRICE_ANY,
    val platforms: Set<String> = emptySet(),
    val tags: List<String> = emptyList(),
) {
    val isDefault: Boolean get() = this == BrowseQuery()
}

internal class BrowsePage(val items: List<CatalogItem>, val code: Int, val cached: Boolean)

internal object ItchBrowse {
    const val SORT_POPULAR = "popular"
    const val SORT_NEW = "new"
    const val SORT_TOP_RATED = "top-rated"
    const val SORT_TOP_SELLERS = "top-sellers"
    val sorts = listOf(SORT_POPULAR, SORT_NEW, SORT_TOP_RATED, SORT_TOP_SELLERS)

    const val PRICE_ANY = "any"
    const val PRICE_FREE = "free"
    const val PRICE_SALE = "sale"
    val prices = listOf(PRICE_ANY, PRICE_FREE, PRICE_SALE)

    private const val BASE = "https://itch.io/games"
    private const val FRESH_MS = 3 * 60 * 60 * 1000L
    private const val GAP_MS = 1_200L
    private const val RETRY_MS = 8_000L
    private const val FOLDER = "itch-browse"

    @Volatile private var lastRequestAt = 0L
    private val lock = Any()

    private fun sortSegment(sort: String): String? = when (sort) {
        SORT_NEW -> "newest"
        SORT_TOP_RATED -> "top-rated"
        SORT_TOP_SELLERS -> "top-sellers"
        else -> null
    }

    /** The ways itch spells a price filter; the first that answers is used, so one wrong guess does not empty the tab. */
    private fun priceSegments(price: String): List<String?> = when (price) {
        PRICE_FREE -> listOf("free", "price-free")
        PRICE_SALE -> listOf("on-sale")
        else -> listOf(null)
    }

    /** Every feed URL that could answer [query] for [page], most likely first. */
    fun urls(query: BrowseQuery, page: Int): List<String> = priceSegments(query.price).map { price ->
        val segments = listOfNotNull(sortSegment(query.sort), price) +
            query.platforms.sorted().map { "platform-$it" } +
            query.tags.map { "tag-" + tagSlug(it) }
        val path = if (segments.isEmpty()) BASE else BASE + "/" + segments.joinToString("/")
        path + ".xml" + if (page > 1) "?page=$page" else ""
    }

    fun tagSlug(tag: String): String = tag.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    /** Page [page] of [query]: the disk copy when it is fresh, otherwise the feed, one request at a time and spaced out. */
    fun page(context: Context, query: BrowseQuery, page: Int, force: Boolean = false): BrowsePage {
        val app = context.applicationContext
        val candidates = urls(query, page)
        if (!force) for (url in candidates) {
            val stored = read(app, url) ?: continue
            return BrowsePage(stored, 200, true)
        }
        var code = 0
        for (url in candidates) {
            val (status, body) = fetch(url)
            code = status
            if (status !in 200..299 || body == null) continue
            val items = ItchShelves.parse(body)
            StoresState.logLine("itch.io browse: $url answered $status with ${items.size} games")
            if (items.isEmpty()) continue
            write(app, url, items)
            return BrowsePage(items, status, false)
        }
        StoresState.logLine("itch.io browse: ${candidates.first()} gave nothing (last status $code)")
        return BrowsePage(emptyList(), code, false)
    }

    private fun fetch(url: String): Pair<Int, String?> {
        synchronized(lock) {
            pause()
            var answer = ItchShelves.open(url)
            if (answer.first == 429) {
                StoresState.logLine("itch.io browse: 429, waiting before one more try")
                Thread.sleep(RETRY_MS)
                pause()
                answer = ItchShelves.open(url)
            }
            lastRequestAt = System.currentTimeMillis()
            return answer
        }
    }

    private fun pause() {
        val gap = GAP_MS - (System.currentTimeMillis() - lastRequestAt)
        if (gap > 0) Thread.sleep(gap)
    }

    private fun file(context: Context, url: String): File {
        val digest = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(context.cacheDir, FOLDER), "$digest.json")
    }

    private fun read(context: Context, url: String): List<CatalogItem>? {
        val file = file(context, url)
        if (!file.isFile || System.currentTimeMillis() - file.lastModified() > FRESH_MS) return null
        return runCatching { ItchCatalog.list(JSONArray(file.readText())) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun write(context: Context, url: String, items: List<CatalogItem>) {
        val file = file(context, url)
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(ItchCatalog.array(items).toString())
        }
    }
}

/** The Store tab's result list: the query, the games loaded so far, and whether more can be asked for. */
internal object ItchBrowseState {
    var query by mutableStateOf(BrowseQuery())
        private set
    var items by mutableStateOf<List<CatalogItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var ended by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set

    private var next = 1
    private var generation = 0

    /** Whether the tab shows these results instead of the front page shelves. */
    val active: Boolean get() = !query.isDefault

    fun choose(context: Context, wanted: BrowseQuery) {
        if (wanted == query) return
        generation++
        query = wanted
        items = emptyList()
        ended = false
        failed = false
        loading = false
        next = 1
        if (active) more(context)
    }

    fun more(context: Context) {
        if (loading || ended || !active) return
        loading = true
        failed = false
        val app = context.applicationContext
        val mine = generation
        val asked = query
        val page = next
        Thread({
            val answer = runCatching { ItchBrowse.page(app, asked, page) }.getOrNull()
            StoresState.post {
                if (mine != generation) return@post
                loading = false
                if (answer == null) {
                    failed = items.isEmpty()
                    ended = items.isNotEmpty()
                    return@post
                }
                if (answer.items.isEmpty()) {
                    failed = items.isEmpty() && answer.code !in 200..299
                    ended = true
                    return@post
                }
                val known = items.mapTo(HashSet()) { it.id }
                val fresh = answer.items.filter { known.add(it.id) }
                if (fresh.isEmpty()) ended = true else {
                    items = items + fresh
                    next = page + 1
                }
            }
        }, "itch-browse").start()
    }
}
