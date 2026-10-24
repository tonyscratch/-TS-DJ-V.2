package com.tonyscratch.tsdj.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonyscratch.tsdj.audio.SampleBank
import com.tonyscratch.tsdj.data.ActionCat
import com.tonyscratch.tsdj.data.Actions
import com.tonyscratch.tsdj.ui.components.Segmented
import com.tonyscratch.tsdj.ui.theme.TS
import com.tonyscratch.tsdj.ui.theme.Type

/**
 * Pick what a shortcut or MY pad should do. Three taps: category, (deck), action.
 * When [fixedDeck] is set (MY pads), the deck choice is hidden.
 */
@Composable
fun ActionPicker(
    title: String,
    banks: List<SampleBank>,
    fixedDeck: Int?,
    onPick: (code: String, deck: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var cat by remember { mutableStateOf(ActionCat.FX) }
    var deckSel by remember { mutableIntStateOf(fixedDeck ?: 0) }
    val actions = remember(banks) { Actions.all(banks) }
    val list = actions.filter { it.cat == cat }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TS.Charcoal,
        title = { Text(title, style = Type.ui(15.sp, 800)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ActionCat.entries) { c ->
                        Chip(text = c.label, on = c == cat, color = TS.Orange) { cat = c }
                    }
                }
                if (fixedDeck == null && list.firstOrNull()?.perDeck == true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Works on", style = Type.ui(12.sp, 700, TS.Text2))
                        Spacer(Modifier.width(10.dp))
                        Segmented(
                            options = listOf("Deck A", "Deck B", "Both"),
                            selected = deckSel,
                            colors = listOf(TS.Orange, TS.Cyan, TS.Amber),
                            onSelect = { deckSel = it },
                            modifier = Modifier.width(240.dp),
                        )
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(list, key = { it.code }) { a ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(TS.Graphite)
                                .border(1.dp, TS.Line2, RoundedCornerShape(8.dp))
                                .semantics { role = Role.Button }
                                .pointerInput(a.code, deckSel) {
                                    detectTapGestures { onPick(a.code, if (a.perDeck) deckSel else -1) }
                                }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(a.label, style = Type.ui(12.sp, 700), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = Type.ui(13.sp, 700, TS.Text2)) }
        },
    )
}
