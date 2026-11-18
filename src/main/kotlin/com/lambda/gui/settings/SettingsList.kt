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
import com.lambda.gui.clickgui.Reveal
import com.lambda.gui.component.Chevron
import com.lambda.gui.component.Expandable
import com.lambda.gui.component.Menu
import com.lambda.gui.component.MenuItem
import com.lambda.gui.component.MenuState
import com.lambda.gui.component.RowHeight
import com.lambda.gui.component.Section
import com.lambda.gui.component.hoverHighlight
import com.lambda.gui.component.mouseClickable
import com.lambda.gui.core.observe
import com.lambda.gui.theme.ContentColor
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.Radius
import com.lambda.gui.theme.Text
import com.lambda.gui.theme.Type

/**
 * The settings of a config as they are organised in code: tabs become a band that picks the
 * page under it, groups become collapsible sections, and a setting appears only while it is
 * visible. A group is nested under its heading, a tab's page framed under its tab.
 */
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
                    Section(layer.name, revealed = Reveal.setting?.let(layer::holds) == true) { Children(layer) }
                else -> Column(Modifier.fillMaxWidth()) {
                    // Whatever is not in a tab is shown above the tabs, whichever one is selected
                    layer.layers.filter { it !is EntryLayer.Tab }.forEach { SettingsList(it) }
                    if (tabs.isNotEmpty()) Tabs(tabs)
                }
            }
        }
    }
}

@Composable
private fun Children(layer: EntryLayer.Multiple<Setting<*>>) = layer.layers.forEach { SettingsList(it) }

/**
 * The pages a config sorts its settings into, and over them a band that says which one is shown
 * and lists the others when clicked.
 *
 * What it picks decides everything under it, so the band is the strongest thing in a panel: as
 * wide as it and filled like an enabled module, where the choice of a setting is a chip at the
 * end of its row. It is one row however many pages there are, and no wider than its settings.
 * Changing pages slides the new one in from the side it is on in the list, while the panel
 * grows or shrinks to fit it.
 */
@Composable
private fun Tabs(tabs: List<EntryLayer.Tab<Setting<*>>>) {
    val shown by observe { tabs.filter { it.hasVisibleSettings() } }
    if (shown.isEmpty()) return
    var selectedName by remember { mutableStateOf(shown.first().name) }
    // The selection can stop having anything visible in it
    val selected = shown.firstOrNull { it.name == selectedName } ?: shown.first()
    // A setting the search points at is on one of the pages: that one is turned to
    val revealed = Reveal.setting
    LaunchedEffect(revealed) {
        if (revealed != null) shown.firstOrNull { it.holds(revealed) }?.let { selectedName = it.name }
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

/** The band over a config's pages: the [selected] one of [names], and a menu of them all under it */
@Composable
private fun TabPicker(names: List<String>, selected: String, onSelect: (String) -> Unit) {
    val colors = LambdaTheme.colors
    val menu = remember { MenuState() }
    var band by remember { mutableStateOf(IntSize.Zero) }
    // The only page left, the others having nothing to show, is a heading rather than a choice of one
    val choice = names.size > 1

    Box(Modifier.fillMaxWidth().onSizeChanged { band = it }) {
        ContentColor(colors.onModuleEnabled) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = RowHeight + 2.dp)
                    .clip(RoundedCornerShape(Radius.Small))
                    .background(colors.moduleEnabled)
                    .then(
                        if (!choice) Modifier
                        else Modifier.hoverHighlight(colors.onModuleEnabled.copy(alpha = 0.12f)).mouseClickable(onClick = menu::toggle)
                    )
                    .padding(horizontal = 6.dp)
            ) {
                Text(selected, Modifier.weight(1f), style = Type.title, maxLines = 1)
                if (choice) Chevron(90f)
            }
        }
        // Drops out from under the band, as wide as it
        Menu(menu.isOpen, onDismiss = menu::dismiss, unfoldFrom = IntSize(band.width, 0)) {
            names.forEach { name ->
                MenuItem(name, selected = name == selected) {
                    if (name != selected) onSelect(name)
                }
            }
        }
    }
}

private const val TAB_MILLIS = 160

/**
 * How one page gives way to another, under tabs or the strip of a settings panel: the new one
 * slides in from the side its tab is on, [towardsEnd] or from the start, while the room they
 * are in grows or shrinks to fit it.
 */
internal fun AnimatedContentTransitionScope<*>.pageTurn(towardsEnd: Boolean): ContentTransform {
    val slide = tween<IntOffset>(TAB_MILLIS)
    return (fadeIn(tween(TAB_MILLIS)) + slideInHorizontally(slide) { if (towardsEnd) it / 5 else -it / 5 })
        .togetherWith(fadeOut(tween(TAB_MILLIS / 2)) + slideOutHorizontally(slide) { if (towardsEnd) -it / 5 else it / 5 })
        .using(SizeTransform(clip = true) { _, _ -> tween(TAB_MILLIS) })
}

/** Whether [setting] is somewhere in the layer, at any depth */
internal fun EntryLayer<Setting<*>>.holds(setting: Setting<*>): Boolean = when (this) {
    is EntryLayer.Single -> entry === setting
    is EntryLayer.Multiple -> layers.any { it.holds(setting) }
    else -> false
}

/** Whether the layer holds a setting that is visible right now, at any depth */
internal fun EntryLayer.Multiple<Setting<*>>.hasVisibleSettings(): Boolean =
    layers.any {
        when (it) {
            is EntryLayer.Single -> it.entry.visibility()
            is EntryLayer.Multiple -> it.hasVisibleSettings()
            else -> false
        }
    }
