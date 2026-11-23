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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lambda.config.serializers.BlockSerializer
import com.lambda.config.settings.collections.CollectionSetting
import com.lambda.config.settings.complex.BlockSetting
import com.lambda.gui.component.Button
import com.lambda.gui.component.Centered
import com.lambda.gui.component.Chip
import com.lambda.gui.component.Dialog
import com.lambda.gui.component.Ellipsis
import com.lambda.gui.component.ItemIcon
import com.lambda.gui.component.Scrollbar
import com.lambda.gui.component.Text
import com.lambda.gui.component.TextInput
import com.lambda.gui.component.hoverHighlight
import com.lambda.gui.component.mouseClickable
import com.lambda.gui.component.wheelScrollable
import com.lambda.gui.core.observe
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.Look
import com.lambda.gui.theme.Type
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries

private val ICON_SIZE = 8.dp

@Composable
internal fun <R : Any> CollectionButton(setting: CollectionSetting<R>) {
    val count by observe { setting.value.size }
    var editing by remember { mutableStateOf(false) }

    Labeled(setting.name) {
        Chip(onClick = { editing = true }) {
            Text("$count selected", Modifier.weight(1f, fill = false), maxLines = 1)
            Ellipsis()
        }
    }
    if (editing) CollectionWindow(setting) { editing = false }
}

@Composable
private fun <R : Any> CollectionWindow(
    setting: CollectionSetting<R>,
    onDismiss: () -> Unit
) {
    val selected by observe { setting.value.toSet() }
    val entries =
        remember(setting.immutableCollection) {
            setting.immutableCollection.map {
                Entry(it, setting.label(it), setting.icon(it))
            }
        }
    var query by remember { mutableStateOf("") }
    val matching =
        remember(entries, query) {
            entries.filter { it.label.contains(query.trim(), ignoreCase = true) }
        }
    val (chosen, available) =
        remember(matching, selected) { matching.partition { it.item in selected } }

    Dialog(onDismiss) {
        Column(Modifier.width(360.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(setting.name)
            Row(
                verticalAlignment = Centered.VERTICALLY,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                TextInput(
                    query,
                    { query = it },
                    Modifier.weight(1f),
                    placeholder = "Search ${setting.name}",
                    focused = true
                )
                Button("Invert", Modifier.height(Look.rowHeight)) {
                    setting.invertSelection()
                }
            }
            Row(
                Modifier.height(220.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Pane("Available (${available.size})", available, Modifier.weight(1f)) {
                    setting.select(it)
                }
                Pane("Selected (${chosen.size})", chosen, Modifier.weight(1f)) {
                    setting.deselect(it)
                }
            }
        }
    }
}

@Composable
internal fun BlockPicker(setting: BlockSetting) {
    val block by observe { setting.value }
    var picking by remember { mutableStateOf(false) }

    Labeled(setting.name) {
        Chip(onClick = { picking = true }) {
            Box(Modifier.size(ICON_SIZE)) {
                ItemIcon(ItemStack(block.asItem()), ICON_SIZE)
            }
            Text(
                BlockSerializer.stringify(block),
                Modifier.weight(1f, fill = false),
                maxLines = 1
            )
            Ellipsis()
        }
    }
    if (!picking) return

    val entries =
        remember {
            Registries.BLOCK.map {
                Entry(it, BlockSerializer.stringify(it), ItemStack(it.asItem()))
            }
        }
    var query by remember { mutableStateOf("") }
    val matching =
        remember(query) {
            entries.filter { it.label.contains(query.trim(), ignoreCase = true) }
        }
    Dialog(onDismiss = { picking = false }) {
        Column(Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(setting.name)
            TextInput(
                query,
                { query = it },
                Modifier.fillMaxWidth(),
                placeholder = "Search blocks",
                focused = true
            )
            Pane("Blocks (${matching.size})", matching, Modifier.height(220.dp)) {
                setting.value = it
                picking = false
            }
        }
    }
}

private class Entry<R>(val item: R, val label: String, val icon: ItemStack?)

@Composable
private fun <R> Pane(
    title: String,
    entries: List<Entry<R>>,
    modifier: Modifier,
    onClick: (R) -> Unit
) {
    val colors = LambdaTheme.colors
    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = colors.onSurfaceVariant, style = Type.label)
        val list = rememberLazyListState()
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(Look.controlRounding))
                .background(colors.input)
        ) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize().wheelScrollable(list)
            ) {
                items(entries) { entry ->
                    Row(
                        verticalAlignment = Centered.VERTICALLY,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Look.rowHeight)
                            .hoverHighlight(colors.controlSelected)
                            .mouseClickable { onClick(entry.item) }
                            .padding(horizontal = 4.dp)
                    ) {
                        if (entry.icon != null) Box(Modifier.size(ICON_SIZE)) {
                            ItemIcon(entry.icon, ICON_SIZE)
                        }
                        Text(entry.label, color = colors.onControl, maxLines = 1)
                    }
                }
            }
            if (list.canScrollForward || list.canScrollBackward) {
                Scrollbar(rememberScrollbarAdapter(list), Modifier.matchParentSize())
            }
        }
    }
}
