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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import com.lambda.config.entries.Setting
import com.lambda.gui.Reveal
import com.lambda.gui.component.ContextMenu
import com.lambda.gui.component.LabeledRow
import com.lambda.gui.component.LocalClickOnRelease
import com.lambda.gui.component.LocalKeyboardReachable
import com.lambda.gui.component.MenuItem
import com.lambda.gui.component.Tooltip
import com.lambda.gui.component.hoverHighlight
import com.lambda.gui.component.rememberLast
import com.lambda.gui.core.awaitFrames
import com.lambda.gui.core.observe
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.Look

private const val SETTLE_MILLIS = 250L
internal val TEXT_FIELD_WIDTH = 90.dp
internal val NUMBER_FIELD_WIDTH = 34.dp
internal val ROW_INSET = 3.dp
internal val ALTERED_GAP = 3.dp
private val ALTERED_DOT = 3.dp

private val LocalAltered = compositionLocalOf { false }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingRow(setting: Setting<*>) {
    val disabled by observe { setting.disabled() }
    val modified by observe { setting.isModified }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var menu by remember { mutableStateOf<IntOffset?>(null) }
    val pointer = remember(setting) { RowPointer(setting) }
    DisposableEffect(pointer) {
        onDispose {
            pointer.hovered = false
            pointer.held = false
        }
    }

    val revealed = Reveal.setting === setting
    val lit by
        animateColorAsState(
            if (revealed) LambdaTheme.colors.onSurface.copy(alpha = 0.35f)
            else Color.Transparent,
            tween(250)
        )
    val intoView = remember { BringIntoViewRequester() }
    LaunchedEffect(revealed) {
        if (revealed) {
            awaitFrames(SETTLE_MILLIS)
            intoView.bringIntoView()
        }
    }

    Tooltip(setting.description, Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .settingCell()
                .bringIntoViewRequester(intoView)
                .onGloballyPositioned { origin = it.positionInWindow() }
                .pointerInput(pointer) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.first()
                            val inside = change.position.x in 0f..size.width.toFloat() && change.position.y in 0f..size.height.toFloat()
                            if (event.type == PointerEventType.Press && event.button == PointerButton.Secondary) {
                                change.consume()
                                menu = (origin + change.position).round()
                                continue
                            }
                            when (event.type) {
                                PointerEventType.Enter -> pointer.hovered = true
                                PointerEventType.Exit -> pointer.hovered = false
                            }
                            val wasHeld = pointer.held
                            pointer.held = event.changes.any { it.pressed }
                            if (wasHeld && !pointer.held && inside) setting.clickListeners.forEach { it() }
                        }
                    }
                }
                .drawWithContent {
                    drawContent()
                    drawRect(lit)
                }
                .enabled(!disabled)
        ) {
            CompositionLocalProvider(
                LocalClickOnRelease provides true,
                LocalKeyboardReachable provides !disabled,
                LocalAltered provides modified
            ) {
                key(setting) { setting.Gui() }
            }
        }
    }

    rememberLast(menu)?.let { position ->
        key(position) {
            ContextMenu(menu != null, position, onDismiss = { menu = null }) {
                MenuItem("Reset", enabled = modified) { setting.reset(silent = true) }
            }
        }
    }
}

private class RowPointer(private val setting: Setting<*>) {
    var hovered = false
        set(value) {
            if (field == value) return
            field = value
            setting.hoverListeners.forEach { it(value) }
        }

    var held = false
        set(value) {
            if (field == value) return
            field = value
            setting.holdListeners.forEach { it(value) }
        }
}

internal fun Modifier.settingCell(): Modifier =
    composed {
        fillMaxWidth()
            .padding(vertical = Look.settingSpacing / 2)
            .clip(RoundedCornerShape(Look.controlRounding))
            .hoverHighlight(LambdaTheme.colors.hover)
    }

private fun Modifier.enabled(enabled: Boolean): Modifier =
    if (enabled) this
    else alpha(0.5f).pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach {
                it.consume()
            }
        }
    }

@Composable
internal fun Labeled(
    name: String,
    modifier: Modifier = Modifier,
    editor: @Composable RowScope.() -> Unit
) = LabeledRow(name, modifier.padding(start = ROW_INSET), { AlteredDot() }, editor)

@Composable
internal fun AlteredDot(color: Color = LambdaTheme.colors.secondary) {
    if (LocalAltered.current) Box(Modifier.size(ALTERED_DOT).background(color, CircleShape))
}
