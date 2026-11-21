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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lambda.config.EntryLayer
import com.lambda.config.MultipleLayerType
import com.lambda.config.entries.Setting
import com.lambda.config.hasVisibleSettings
import com.lambda.config.holds
import com.lambda.gui.Reveal
import com.lambda.gui.component.Chevron
import com.lambda.gui.component.ContentColor
import com.lambda.gui.component.Expandable
import com.lambda.gui.component.Menu
import com.lambda.gui.component.MenuItem
import com.lambda.gui.component.MenuState
import com.lambda.gui.component.Section
import com.lambda.gui.component.Text
import com.lambda.gui.component.hoverHighlight
import com.lambda.gui.component.mouseClickable
import com.lambda.gui.core.observe
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.Look
import com.lambda.gui.theme.Motion
import com.lambda.gui.theme.Type

@Composable
fun SettingsList(layer: EntryLayer<Setting<*>>) {
    when (layer) {
        is EntryLayer.Single -> {
            val setting = layer.entry
            val visible by observe { setting.visibility() }
            Expandable(visible) { SettingRow(setting) }
        }

        is EntryLayer.Multiple -> {
            val hasVisibleSettings by observe { layer.hasVisibleSettings() }
            if (!hasVisibleSettings) return

            val tabs = layer.layers.filterIsInstance<EntryLayer.Tab<Setting<*>>>()
            when {
                layer.multipleType == MultipleLayerType.Group ->
                    Section(
                        layer.name,
                        revealed = Reveal.setting?.let(layer::holds) == true
                    ) {
                        Children(layer)
                    }
                else ->
                    Column(Modifier.fillMaxWidth()) {
                        layer.layers.filter { it !is EntryLayer.Tab }.forEach { SettingsList(it) }
                        if (tabs.isNotEmpty()) Tabs(tabs)
                    }
            }
        }
    }
}

@Composable
private fun Children(layer: EntryLayer.Multiple<Setting<*>>) =
    layer.layers.forEach { SettingsList(it) }

@Composable
private fun Tabs(tabs: List<EntryLayer.Tab<Setting<*>>>) {
    val shown by observe { tabs.filter { it.hasVisibleSettings() } }
    if (shown.isEmpty()) return
    var selectedName by remember { mutableStateOf(shown.first().name) }
    val selected = shown.firstOrNull { it.name == selectedName } ?: shown.first()
    val revealed = Reveal.setting
    LaunchedEffect(revealed) {
        if (revealed != null) {
            shown.firstOrNull { it.holds(revealed) }?.let { selectedName = it.name }
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 1.dp)) {
        TabPicker(shown.map { it.name }, selected.name) { selectedName = it }
        AnimatedContent(
            targetState = selected,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = { pageTurn(towardsEnd = shown.indexOf(targetState) > shown.indexOf(initialState)) },
        ) { tab ->
            Column(Modifier.fillMaxWidth().padding(vertical = 1.dp)) { Children(tab) }
        }
    }
}

@Composable
private fun TabPicker(names: List<String>, selected: String, onSelect: (String) -> Unit) {
    val colors = LambdaTheme.colors
    val menu = remember { MenuState() }
    var band by remember { mutableStateOf(IntSize.Zero) }
    val choice = names.size > 1

    Box(Modifier.fillMaxWidth().onSizeChanged { band = it }) {
        ContentColor(colors.onHeader) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Look.rowHeight + 2.dp)
                    .clip(RoundedCornerShape(topStart = Look.windowRounding, topEnd = Look.windowRounding))
                    .background(colors.header)
                    .then(
                        if (!choice) Modifier
                        else {
                            Modifier
                                .hoverHighlight(colors.onHeader.copy(alpha = 0.12f))
                                .mouseClickable(onClick = menu::toggle)
                        }
                    )
                    .padding(horizontal = 6.dp)
            ) {
                Text(selected, Modifier.weight(1f), style = Type.title, maxLines = 1)
                if (choice) Chevron(90f)
            }
        }
        Menu(
            menu.isOpen,
            onDismiss = menu::dismiss,
            unfoldFrom = IntSize(band.width, 0),
            attached = true
        ) {
            names.forEach { name ->
                MenuItem(name, selected = name == selected) {
                    if (name != selected) onSelect(name)
                }
            }
        }
    }
}

internal fun AnimatedContentTransitionScope<*>.pageTurn(towardsEnd: Boolean): ContentTransform {
    val millis = Motion.pageMillis
    val slide = tween<IntOffset>(millis)
    return (fadeIn(tween(millis)) + slideInHorizontally(slide) { if (towardsEnd) it / 5 else -it / 5 })
        .togetherWith(fadeOut(tween(millis / 2)) + slideOutHorizontally(slide) { if (towardsEnd) -it / 5 else it / 5 })
        .using(SizeTransform(clip = true) { _, _ -> tween(millis) })
}
