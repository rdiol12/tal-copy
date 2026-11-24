package com.tal.workhours

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LanguageSelector() {
    val selected = appLanguages.find { it.tag == AppText.preference }
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(tr(R.string.language_title) + " · " + (selected?.name ?: tr(R.string.language_system)))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            val choices = listOf(AppLanguage("", tr(R.string.language_system))) + appLanguages
            choices.forEach { language ->
                DropdownMenuItem(text = { Text(language.name) }, onClick = {
                    expanded = false
                    AppText.select(language.tag)
                })
            }
        }
    }
    Text(tr(R.string.language_hint), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp))
}
