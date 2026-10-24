package com.droiddeck.launcher.stores.itch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItchBrowseTest {
    @Test fun theDefaultQueryIsTheFrontPage() {
        assertTrue(BrowseQuery().isDefault)
        assertFalse(BrowseQuery(sort = ItchBrowse.SORT_NEW).isDefault)
        assertFalse(BrowseQuery(price = ItchBrowse.PRICE_FREE).isDefault)
    }

    @Test fun sortsMapToTheirFeedPaths() {
        assertEquals(listOf("https://itch.io/games.xml"), ItchBrowse.urls(BrowseQuery(), 1))
        assertEquals(listOf("https://itch.io/games/newest.xml"), ItchBrowse.urls(BrowseQuery(sort = ItchBrowse.SORT_NEW), 1))
        assertEquals(listOf("https://itch.io/games/top-rated.xml"), ItchBrowse.urls(BrowseQuery(sort = ItchBrowse.SORT_TOP_RATED), 1))
        assertEquals(listOf("https://itch.io/games/top-sellers.xml"), ItchBrowse.urls(BrowseQuery(sort = ItchBrowse.SORT_TOP_SELLERS), 1))
    }

    @Test fun pagesAfterTheFirstCarryThePageNumber() {
        assertEquals(listOf("https://itch.io/games/newest.xml?page=3"), ItchBrowse.urls(BrowseQuery(sort = ItchBrowse.SORT_NEW), 3))
    }

    @Test fun aPriceFilterOffersEverySpellingInOrder() {
        val free = ItchBrowse.urls(BrowseQuery(sort = ItchBrowse.SORT_TOP_RATED, price = ItchBrowse.PRICE_FREE), 1)
        assertEquals(listOf("https://itch.io/games/top-rated/free.xml", "https://itch.io/games/top-rated/price-free.xml"), free)
        assertEquals(listOf("https://itch.io/games/on-sale.xml"), ItchBrowse.urls(BrowseQuery(price = ItchBrowse.PRICE_SALE), 1))
    }

    @Test fun platformsAndTagsFollowTheSortAndPriceInAFixedOrder() {
        val query = BrowseQuery(
            sort = ItchBrowse.SORT_NEW, price = ItchBrowse.PRICE_SALE,
            platforms = setOf("web", "linux"), tags = listOf("Pixel Art", "Horror"),
        )
        assertEquals(
            listOf("https://itch.io/games/newest/on-sale/platform-linux/platform-web/tag-pixel-art/tag-horror.xml"),
            ItchBrowse.urls(query, 1),
        )
    }

    @Test fun tagsBecomeSlugs() {
        assertEquals("pixel-art", ItchBrowse.tagSlug(" Pixel Art "))
        assertEquals("2d", ItchBrowse.tagSlug("2D"))
        assertEquals("sci-fi", ItchBrowse.tagSlug("Sci-Fi!"))
    }

    @Test fun theSamePlatformsInAnyOrderGiveTheSameUrl() {
        val a = ItchBrowse.urls(BrowseQuery(platforms = setOf("web", "linux")), 1)
        val b = ItchBrowse.urls(BrowseQuery(platforms = setOf("linux", "web")), 1)
        assertEquals(a, b)
    }
}
