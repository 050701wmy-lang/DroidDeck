package com.droiddeck.launcher.ui

import com.droiddeck.launcher.update.AppUpdates
import org.junit.Assert.*
import org.junit.Test

class ChangelogChangesTest {
    @Test fun publishedNotesRemainVisibleWithoutAnyNetworkCache() {
        val changes = changelogChanges(null)
        assertTrue(changes.isNotEmpty())
        assertTrue(changes.all { it.publishedAt > 0 && it.commit in changelogNotes })
    }

    @Test fun anOldCacheDoesNotHideNewerBundledNotesOrDuplicateThem() {
        val oldest = changelogNotes.minBy { it.value.third }
        val newest = changelogNotes.maxBy { it.value.third }
        val cached = AppUpdates.PreviewChange(oldest.key, "Cached title", "Cached summary", oldest.value.third)
        val catalog = AppUpdates.Catalog(null, null, emptyList(), 1L, listOf(cached))
        val changes = changelogChanges(catalog)
        assertEquals(newest.key, changes.first().commit)
        assertEquals(1, changes.count { it.commit == oldest.key })
        assertSame(cached, changes.single { it.commit == oldest.key })
    }

    @Test fun newlyPublishedNetworkNotesStillComeBeforeTheBundledSnapshot() {
        val newer = AppUpdates.PreviewChange("f".repeat(40), "New preview", "New notes",
            changelogNotes.maxOf { it.value.third } + 1000)
        val changes = changelogChanges(AppUpdates.Catalog(null, null, emptyList(), 1L, listOf(newer)))
        assertSame(newer, changes.first())
        assertTrue(changes.any { it.commit in changelogNotes })
    }
}
