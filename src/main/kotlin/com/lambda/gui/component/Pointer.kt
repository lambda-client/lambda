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

package com.lambda.gui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.onDrag
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize

@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.mouseClickable(
    enabled: Boolean = true,
    keyboard: Boolean = true,
    onRightClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
): Modifier =
    if (!enabled) this
    else composed {
        val onRelease = LocalClickOnRelease.current
        val actionFor by rememberUpdatedState { button: PointerButton? ->
            when (button) {
                PointerButton.Primary -> onClick
                PointerButton.Secondary -> onRightClick
                else -> null
            }
        }
        val keys =
            if (!keyboard) Modifier else Modifier.keyboardFocusable { event ->
                val action = if (event.isShiftPressed) onRightClick else onClick ?: onRightClick
                if (event.isActivation) action?.invoke()
                event.isActivation && action != null
            }
        keys.pointerInput(onRelease) {
            awaitPointerEventScope {
                while (true) {
                    val press = awaitPointerEvent()
                    val down = press.changes.first()
                    val button = press.button
                    if (press.type != PointerEventType.Press || down.isConsumed || actionFor(button) == null) continue
                    down.consume()
                    if (!onRelease) {
                        actionFor(button)?.invoke()
                        continue
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()
                        if (event.type == PointerEventType.Press) break
                        if (event.type != PointerEventType.Release || event.button != button) continue
                        change.consume()
                        val inside = change.position.x in 0f..size.width.toFloat() && change.position.y in 0f..size.height.toFloat()
                        if (inside) actionFor(button)?.invoke()
                        break
                    }
                }
            }
        }
    }

val LocalClickOnRelease = compositionLocalOf { false }

fun Modifier.onMousePress(button: PointerButton, onPress: (position: Offset) -> Unit): Modifier =
    onPress { pressed, position ->
        if (pressed == button) onPress(position)
        pressed == button
    }

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.onPress(handle: (button: PointerButton?, position: Offset) -> Boolean): Modifier =
    composed {
        val currentHandle by rememberUpdatedState(handle)
        pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.first()
                    if (event.type != PointerEventType.Press || change.isConsumed) continue
                    if (currentHandle(event.button, change.position)) change.consume()
                }
            }
        }
    }

fun Modifier.consumeClicks(): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Press) event.changes.forEach { it.consume() }
            }
        }
    }

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.mouseDraggable(
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDrag: (Offset) -> Unit,
): Modifier =
    onDrag(
        matcher = PointerMatcher.mouse(PointerButton.Primary),
        onDragStart = { onDragStart() },
        onDragCancel = onDragEnd,
        onDragEnd = onDragEnd,
        onDrag = onDrag,
    )

fun Modifier.scrubbable(onEnd: () -> Unit = {}, onScrub: (position: Offset, size: IntSize) -> Unit): Modifier =
    composed {
        val currentOnScrub by rememberUpdatedState(onScrub)
        val currentOnEnd by rememberUpdatedState(onEnd)
        pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                currentOnScrub(down.position, size)
                drag(down.id) { change ->
                    change.consume()
                    currentOnScrub(change.position, size)
                }
                currentOnEnd()
            }
        }
    }

fun Modifier.hoverHighlight(color: Color): Modifier =
    composed {
        val interactions = remember { MutableInteractionSource() }
        val hovered by interactions.collectIsHoveredAsState()
        hoverable(interactions).background(if (hovered) color else Color.Transparent)
    }
