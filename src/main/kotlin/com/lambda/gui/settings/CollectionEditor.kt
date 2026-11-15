/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.gui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lambda.config.serializers.BlockSerializer
import com.lambda.config.settings.collections.CollectionSetting
import com.lambda.config.settings.complex.BlockSetting
import com.lambda.gui.component.Button
import com.lambda.gui.component.Ellipsis
import com.lambda.gui.component.Chip
import com.lambda.gui.component.Dialog
import com.lambda.gui.component.RowHeight
import com.lambda.gui.component.Scrollbar
import com.lambda.gui.component.TextInput
import com.lambda.gui.component.hoverHighlight
import com.lambda.gui.component.mouseClickable
import com.lambda.gui.component.wheelScrollable
import com.lambda.gui.core.observe
import com.lambda.gui.hud.ItemIcon
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.Radius
import com.lambda.gui.theme.Text
import com.lambda.gui.theme.Type
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries

/**
 * A collection setting: its name and, on a chip, how much of it is selected. The chip opens the
 * editor in a window of its own, as the lists it holds would never fit into a row.
 */
@Composable
internal fun <R : Any> CollectionButton(setting: CollectionSetting<R>) {
    val count by observe { setting.value.size }
    var editing by remember { mutableStateOf(false) }

    Labeled(setting.name) {
        Chip(onClick = { editing = true }) {
            Text("$count selected", maxLines = 1)
            Ellipsis()
        }
    }
    if (editing) CollectionWindow(setting) { editing = false }
}

/**
 * Two lists side by side, what can be selected and what is. Clicking an entry moves it across,
 * and the search narrows both.
 */
@Composable
private fun <R : Any> CollectionWindow(setting: CollectionSetting<R>, onDismiss: () -> Unit) {
    // Selections are edited in place, so a copy is what tells one frame's selection from the next
    val selected by observe { setting.value.toSet() }
    val entries = remember(setting.immutableCollection) {
        setting.immutableCollection.map { Entry(it, setting.label(it), setting.icon(it)) }
    }
    var query by remember { mutableStateOf("") }
    val matching = remember(entries, query) { entries.filter { it.label.contains(query.trim(), ignoreCase = true) } }
    val (chosen, available) = remember(matching, selected) { matching.partition { it.item in selected } }

    Dialog(onDismiss) {
        Column(Modifier.width(360.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(setting.name)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                TextInput(query, { query = it }, Modifier.weight(1f), placeholder = "Search ${setting.name}", focused = true)
                Button("Invert", Modifier.height(RowHeight)) { setting.invertSelection() }
            }
            Row(Modifier.height(220.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Pane("Available (${available.size})", available, Modifier.weight(1f)) { setting.select(it) }
                Pane("Selected (${chosen.size})", chosen, Modifier.weight(1f)) { setting.deselect(it) }
            }
        }
    }
}

/**
 * A setting that is one block: its name and, on a chip, the block with its icon. The chip opens
 * a window listing every block, where clicking one picks it.
 */
@Composable
internal fun BlockPicker(setting: BlockSetting) {
    val block by observe { setting.value }
    var picking by remember { mutableStateOf(false) }

    Labeled(setting.name) {
        Chip(onClick = { picking = true }) {
            Box(Modifier.size(IconSize)) { ItemIcon(ItemStack(block.asItem()), IconSize) }
            Text(BlockSerializer.stringify(block), maxLines = 1)
            Ellipsis()
        }
    }
    if (!picking) return

    val entries = remember { Registries.BLOCK.map { Entry(it, BlockSerializer.stringify(it), ItemStack(it.asItem())) } }
    var query by remember { mutableStateOf("") }
    val matching = remember(query) { entries.filter { it.label.contains(query.trim(), ignoreCase = true) } }
    Dialog(onDismiss = { picking = false }) {
        Column(Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(setting.name)
            TextInput(query, { query = it }, Modifier.fillMaxWidth(), placeholder = "Search blocks", focused = true)
            Pane("Blocks (${matching.size})", matching, Modifier.height(220.dp)) {
                setting.value = it
                picking = false
            }
        }
    }
}

/** One of the things a collection can hold, with what it is shown as */
private class Entry<R>(val item: R, val label: String, val icon: ItemStack?)

@Composable
private fun <R> Pane(title: String, entries: List<Entry<R>>, modifier: Modifier, onClick: (R) -> Unit) {
    val colors = LambdaTheme.colors
    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = colors.onSurfaceVariant, style = Type.label)
        // Lazy, because a list of every block or item in the game runs to over a thousand rows
        val list = rememberLazyListState()
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(Radius.Small)).background(colors.input)) {
            LazyColumn(state = list, modifier = Modifier.fillMaxSize().wheelScrollable(list)) {
                items(entries) { entry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = RowHeight)
                            .hoverHighlight(colors.controlSelected)
                            .mouseClickable { onClick(entry.item) }
                            .padding(horizontal = 4.dp)
                    ) {
                        // A slot of the same width for every entry, even one with nothing in it, so the names line up
                        if (entry.icon != null) Box(Modifier.size(IconSize)) { ItemIcon(entry.icon, IconSize) }
                        Text(entry.label, color = colors.onControl, maxLines = 1)
                    }
                }
            }
            if (list.canScrollForward || list.canScrollBackward) Scrollbar(rememberScrollbarAdapter(list), Modifier.matchParentSize())
        }
    }
}

/** Edge length of an entry's icon: the 16 pixels Minecraft draws an item at, at 1080p */
private val IconSize = 8.dp
