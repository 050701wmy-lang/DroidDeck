package com.steamdeck.launcher.files

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.steamdeck.launcher.ui.SteamDeckTheme
import java.io.File

/**
 * Themed host for [FilePickerScreen]. Launched through [InAppFilePicker] and answers with the
 * picked path in [EXTRA_SELECTED_FILE] on RESULT_OK.
 *
 * Extras:
 *  - [EXTRA_EXTENSIONS]        allowed lowercase extensions, no dot; absent or empty = all files
 *  - [EXTRA_PICK_DIRECTORY]    true to pick a folder instead of a file
 *  - [EXTRA_INITIAL_DIRECTORY] where to open; absent = the last folder picked from, then internal storage
 *  - [EXTRA_PICKER_TITLE]      the header
 */
class FilePickerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 28: the old storage permission is exactly what lets us list the card.
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        }

        val extensions = intent.getStringArrayExtra(EXTRA_EXTENSIONS)?.toList() ?: emptyList()
        val pickDir = intent.getBooleanExtra(EXTRA_PICK_DIRECTORY, false)
        val title = intent.getStringExtra(EXTRA_PICKER_TITLE)
        val prefs = getSharedPreferences("picker", MODE_PRIVATE)
        val initialDir = (intent.getStringExtra(EXTRA_INITIAL_DIRECTORY) ?: prefs.getString("lastDir", null))
            ?.let { File(it) }?.takeIf { it.isDirectory }

        setContent {
            SteamDeckTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    FilePickerScreen(
                        pickDirMode = pickDir,
                        pickExtensions = extensions,
                        initialDir = initialDir,
                        title = title ?: if (pickDir) "Choose a folder" else "Choose a file",
                        onPick = { file ->
                            val remembered = if (file.isDirectory) file else file.parentFile
                            remembered?.let { prefs.edit().putString("lastDir", it.absolutePath).apply() }
                            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_SELECTED_FILE, file.absolutePath))
                            finish()
                        },
                        onCancel = { setResult(Activity.RESULT_CANCELED); finish() },
                    )
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // The screen re-lists on its own when the grant lands; nothing to relay.
        if (requestCode == 1 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) recreate()
    }

    companion object {
        const val EXTRA_EXTENSIONS = "extensions"
        const val EXTRA_PICK_DIRECTORY = "pickDirectory"
        const val EXTRA_INITIAL_DIRECTORY = "initialDirectory"
        const val EXTRA_PICKER_TITLE = "pickerTitle"
        const val EXTRA_SELECTED_FILE = "selectedFile"
    }
}
