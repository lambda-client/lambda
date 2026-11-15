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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lambda.config.EntryLayer
import com.lambda.config.MultipleLayerType
import com.lambda.config.entries.Setting
import com.lambda.gui.clickgui.Reveal
import com.lambda.gui.component.Expandable
import com.lambda.gui.component.Section
import com.lambda.gui.component.mouseClickable
import com.lambda.gui.core.observe
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.Radius
import com.lambda.gui.theme.Text
import com.lambda.gui.theme.Type

/**
 * The settings of a config as they are organised in code: tabs become a strip that swaps the
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
 * Tabs over the page of whichever one is chosen. The names wrap onto as many lines as they
 * need, as splitting the width evenly would squeeze long ones in a narrow panel.
 *
 * The tabs decide everything that is shown under them, so they are the strongest thing in a
 * panel: a band across its width, with the chosen tab filled in the colour of a window's header.
 * Changing tabs slides the new page in from the side its tab is on while the page grows or
 * shrinks to fit in height; in width the pages are kept alike.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tabs(tabs: List<EntryLayer.Tab<Setting<*>>>) {
    val colors = LambdaTheme.colors
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
    // How wide each page wanted to be when it was last shown. Every page is given the width of
    // the widest, so changing tabs does not change the width of the panel, while a page that
    // gets narrower, a group in it having been folded away, lets the panel shrink again.
    val widths = remember { HashMap<String, Int>() }

    Column(Modifier.fillMaxWidth().padding(top = 1.dp)) {
        val shape = RoundedCornerShape(Radius.Small)
        FlowRow(Modifier.fillMaxWidth().clip(shape).background(colors.raised)) {
            shown.forEach { tab ->
                val isSelected = tab === selected
                // Filled like an enabled module: the same bright, see-through crimson for "this one is on"
                val fill by animateColorAsState(if (isSelected) colors.moduleEnabled else Color.Transparent, tween(TAB_MILLIS))
                Text(
                    text = tab.name,
                    color = if (isSelected) colors.onModuleEnabled else colors.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        // The only tab left, the others having nothing to show, takes the whole
                        // strip: a heading over one page rather than a choice of one
                        .then(if (shown.size == 1) Modifier.fillMaxWidth() else Modifier)
                        .clip(shape)
                        .background(fill)
                        .mouseClickable { selectedName = tab.name }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        // ...with its name in the middle, as a heading's is
                        .then(if (shown.size == 1) Modifier.wrapContentWidth(Alignment.CenterHorizontally) else Modifier)
                )
            }
        }
        AnimatedContent(
            targetState = selected,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = { pageTurn(towardsEnd = shown.indexOf(targetState) > shown.indexOf(initialState)) },
        ) { tab ->
            Column(
                Modifier
                    .layout { measurable, constraints ->
                        widths[tab.name] = measurable.maxIntrinsicWidth(constraints.maxHeight)
                        val widest = widths.values.max().coerceIn(constraints.minWidth, constraints.maxWidth)
                        val placeable = measurable.measure(constraints.copy(minWidth = widest))
                        // Stated outright as well: asked how wide it would like to be, rather
                        // than measured, the page answers through this without the minimum applied
                        layout(maxOf(placeable.width, widest), placeable.height) { placeable.place(0, 0) }
                    }
                    .fillMaxWidth()
                    .padding(vertical = 1.dp)
            ) { Children(tab) }
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
