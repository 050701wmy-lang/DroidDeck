package com.droiddeck.launcher.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import com.droiddeck.launcher.R
import com.droiddeck.launcher.core.GitHubDownloads

@Composable
internal fun GitHubDownloadSettings(host: MenuHost) {
    val context = LocalContext.current
    var prefix by remember { mutableStateOf(GitHubDownloads.current()) }
    var editing by remember { mutableStateOf(false) }
    SettingsGroup(stringResource(R.string.github_download_group)) {
        ToggleRow(host, "github-accelerator", stringResource(R.string.github_download_enabled),
            stringResource(R.string.github_download_hint), prefix.isNotEmpty()) { enabled ->
            prefix = if (enabled) GitHubDownloads.DEFAULT else ""
            GitHubDownloads.save(context, prefix)
        }
        ActionRow(stringResource(R.string.github_download_address),
            prefix.ifEmpty { stringResource(R.string.github_download_off) },
            stringResource(R.string.mode_sgdb_edit), { editing = true })
    }
    if (editing) {
        var draft by remember { mutableStateOf(prefix) }
        val valid = runCatching { GitHubDownloads.normalize(draft) }.isSuccess
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.github_download_address)) },
            text = {
                Column {
                    Text(stringResource(R.string.github_download_address_hint))
                    OutlinedTextField(draft, { draft = it }, singleLine = true,
                        isError = !valid, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri))
                    if (!valid) Text(stringResource(R.string.github_download_invalid))
                }
            },
            confirmButton = { TextButton(onClick = {
                GitHubDownloads.save(context, draft)
                prefix = GitHubDownloads.current()
                editing = false
            }, enabled = valid) { Text(stringResource(R.string.common_ok)) } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}
