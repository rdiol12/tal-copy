package com.tal.workhours

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Stable card IDs; tolerate older layouts, duplicates and damaged preferences. */
fun normalizeSnapshotCardOrder(saved: String?): List<Int> {
    val known = (0..11).filter { it != 7 }
    val selected = saved.orEmpty().split(',').mapNotNull { it.toIntOrNull() }.filter { it in known }.distinct()
    return selected + known.filterNot { it in selected }
}

@Composable
fun SnapshotOrderDialog(order: List<Int>, titles: List<String>, onDismiss: () -> Unit, onSave: (List<Int>) -> Unit) {
    var draft by remember { mutableStateOf(order) }
    AlertDialog(onDismissRequest=onDismiss,title={Text(tr(R.string.snapshot_arrange))},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            draft.forEachIndexed { index, card ->
                val up=tr(R.string.snapshot_move_up)+" · "+titles[card]
                val down=tr(R.string.snapshot_move_down)+" · "+titles[card]
                Row(Modifier.fillMaxWidth()) {
                    Text(titles[card],modifier=Modifier.weight(1f).padding(vertical=12.dp))
                    TextButton(enabled=index>0,modifier=Modifier.semantics { contentDescription=up },onClick={
                        draft=draft.toMutableList().also { it[index]=it[index-1];it[index-1]=card }
                    }) { Text("↑") }
                    TextButton(enabled=index<draft.lastIndex,modifier=Modifier.semantics { contentDescription=down },onClick={
                        draft=draft.toMutableList().also { it[index]=it[index+1];it[index+1]=card }
                    }) { Text("↓") }
                }
            }
            TextButton(onClick={draft=normalizeSnapshotCardOrder(null)}){Text(tr(R.string.snapshot_reset_order))}
        }
    },confirmButton={TextButton(onClick={onSave(draft)}){Text(tr(R.string.snapshot_save_order))}},
        dismissButton={TextButton(onClick=onDismiss){Text(tr(R.string.snapshot_cancel_order))}})
}
