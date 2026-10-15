@file:OptIn(ExperimentalMaterial3Api::class)

package com.c2rd.telephone.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ScriptScreen(script: String, onScriptChange: (String) -> Unit, onStart: () -> Unit) {
    val context = LocalContext.current
    var field by remember { mutableStateOf(TextFieldValue(script)) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }

    // Script can change from outside (share intent, import).
    LaunchedEffect(script) {
        if (script != field.text) field = TextFieldValue(script, TextRange(script.length))
    }
    fun update(v: TextFieldValue) {
        field = v
        if (v.text != script) onScriptChange(v.text)
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { readText(context, it) }?.let(onScriptChange)
    }

    val words = remember(script) { script.split(Regex("\\s+")).count { it.isNotBlank() } }
    val minutes = words / 150.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tele-phone") },
                actions = {
                    IconButton(onClick = { importer.launch(arrayOf("text/*")) }) {
                        Icon(Icons.Default.FileOpen, contentDescription = "Import text file")
                    }
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear script")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onStart,
                icon = { Icon(Icons.Default.Videocam, contentDescription = null) },
                text = { Text("Record") },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 16.dp)
        ) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Edit") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Preview") })
            }
            Text(
                "$words words · about ${"%.1f".format(minutes)} min at 150 wpm",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            if (tab == 0) {
                FormatBar(field, ::update)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = field,
                    onValueChange = ::update,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 80.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    placeholder = { Text("Paste or type your script. Markdown works.") },
                )
            } else {
                Text(
                    Markdown.render(script),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black, RoundedCornerShape(12.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                        .padding(bottom = 80.dp),
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear script?") },
            text = { Text("This deletes the current script text.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; update(TextFieldValue("")) }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FormatBar(field: TextFieldValue, onChange: (TextFieldValue) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FormatButton("B") { onChange(wrap(field, "**")) }
        FormatButton("I") { onChange(wrap(field, "*")) }
        FormatButton("Hi") { onChange(wrap(field, "==")) }
        FormatButton("S") { onChange(wrap(field, "~~")) }
        FormatButton("H1") { onChange(togglePrefix(field, "# ")) }
        FormatButton("H2") { onChange(togglePrefix(field, "## ")) }
        FormatButton("•") { onChange(togglePrefix(field, "- ")) }
        FormatButton("❝") { onChange(togglePrefix(field, "> ")) }
        FormatButton("[cue]") { onChange(insert(field, "[pause]")) }
    }
}

@Composable
private fun FormatButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
    ) { Text(label, fontWeight = FontWeight.Bold) }
}

/** Wraps the selection in [token]; with no selection, drops the cursor between a pair. */
private fun wrap(v: TextFieldValue, token: String): TextFieldValue {
    val sel = v.selection
    val start = sel.min
    val end = sel.max
    val text = v.text.substring(0, start) + token + v.text.substring(start, end) + token + v.text.substring(end)
    return if (sel.collapsed) {
        TextFieldValue(text, TextRange(start + token.length))
    } else {
        TextFieldValue(text, TextRange(start + token.length, end + token.length))
    }
}

/** Adds or removes [prefix] at the start of the line the cursor is on. */
private fun togglePrefix(v: TextFieldValue, prefix: String): TextFieldValue {
    val lineStart = v.text.lastIndexOf('\n', v.selection.min - 1) + 1
    val rest = v.text.substring(lineStart)
    val cursor = v.selection.min
    return if (rest.startsWith(prefix)) {
        TextFieldValue(
            v.text.substring(0, lineStart) + rest.removePrefix(prefix),
            TextRange((cursor - prefix.length).coerceAtLeast(lineStart)),
        )
    } else {
        // Swap heading levels instead of stacking "# ## ".
        val stripped = rest.replaceFirst(Regex("""^(#{1,6}\s+|[-*+]\s+|>\s?)"""), "")
        val removed = rest.length - stripped.length
        TextFieldValue(
            v.text.substring(0, lineStart) + prefix + stripped,
            TextRange((cursor - removed).coerceAtLeast(lineStart) + prefix.length),
        )
    }
}

private fun insert(v: TextFieldValue, s: String): TextFieldValue {
    val text = v.text.substring(0, v.selection.min) + s + v.text.substring(v.selection.max)
    return TextFieldValue(text, TextRange(v.selection.min + s.length))
}

fun readText(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
}.getOrNull()
