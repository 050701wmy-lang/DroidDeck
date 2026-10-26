package com.droiddeck.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droiddeck.launcher.R
import com.droiddeck.launcher.update.AppUpdates

class UpdatesState(
    val catalog: AppUpdates.Catalog? = null,
    val checking: Boolean = false,
    val error: String? = null,
)

class UpdatesActions(val onCheck: () -> Unit = {})

@Composable
internal fun UpdatesPage(s: FrontEndState, a: FrontEndActions, modifier: Modifier) {
    val u = s.updates
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val me = remember { AppUpdates.installed() }
    val originalNotes = booleanResource(R.bool.changelog_original_notes)
    val changes = remember(u.catalog) {
        buildList {
            u.catalog?.preview?.let {
                add(AppUpdates.PreviewChange(it.commit, it.title.ifBlank { it.tag }, it.summary, it.publishedAt))
            }
            addAll(u.catalog?.recentPreviews.orEmpty())
        }.distinctBy { it.commit.lowercase() }.sortedByDescending { it.publishedAt }
    }
    Column(modifier = modifier.testTag("changelog-page")) {
        PageHeader(stringResource(R.string.upd_title)) {
            Spacer(Modifier.weight(1f))
            ToolIcon(Icons.Outlined.Refresh, stringResource(R.string.stores_refresh), busy = u.checking,
                enabled = !u.checking, onClick = a.updates.onCheck)
        }
        Text(stringResource(R.string.upd_build, me.version, s.buildLabel),
            fontSize = 12.5.sp, color = colors.onSurfaceVariant)
        u.catalog?.checkedAt?.takeIf { it > 0 }?.let {
            Text(stringResource(R.string.upd_checked, ago(it)), fontSize = 12.sp, color = colors.onSurfaceVariant)
        }
        if (u.error != null) Text(u.error, fontSize = 13.sp, color = pal.error,
            modifier = Modifier.padding(top = 8.dp))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (changes.isEmpty()) Text(
                stringResource(if (u.checking) R.string.common_checking else R.string.changelog_empty),
                color = colors.onSurfaceVariant)
            changes.forEach { change ->
                val notes = changelogNotes[change.commit.lowercase()]
                val title = when {
                    notes != null -> stringResource(notes.first)
                    originalNotes -> change.title.ifBlank { change.commit.take(7) }
                    else -> stringResource(R.string.changelog_untranslated_title, change.commit.take(7))
                }
                val summary = when {
                    notes != null -> if (notes.second != 0) stringResource(notes.second) else ""
                    originalNotes -> change.summary
                    else -> stringResource(R.string.changelog_translation_pending)
                }
                Column(Modifier.fillMaxWidth().clip(Shape16).background(colors.surface)
                    .border(1.dp, pal.line, Shape16).padding(16.dp)) {
                    if (change.commit.equals(me.commit, ignoreCase = true)) Text(
                        stringResource(R.string.changelog_installed), fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold, color = pal.good)
                    Text(title, fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                    Text("${ago(change.publishedAt)} · ${change.commit.take(7)}", fontSize = 12.sp,
                        color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    if (summary.isNotBlank()) Text(summary, fontSize = 14.sp,
                        color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun ago(millis: Long): String {
    if (millis <= 0) return stringResource(R.string.upd_while_ago)
    if (System.currentTimeMillis() - millis in 0 until 60_000) return stringResource(R.string.upd_just_now)
    return android.text.format.DateUtils.getRelativeTimeSpanString(
        millis, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS,
    ).toString()
}
