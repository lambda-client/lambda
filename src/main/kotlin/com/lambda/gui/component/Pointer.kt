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

import androidx.compose.animation.core.EaseOutQuint
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.ScrollableState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Makes an element respond to mouse clicks, one callback per button.
 *
 * ```
 * Modifier.mouseClickable(onRightClick = { openSettings() }) { module.toggle() }
 * ```
 *
 * A click acts the moment the button goes down: nothing waits for the release, for a double
 * click that might follow, or for an animation to finish, so the interface answers at once
 * however fast it is clicked.
 *
 * Under [LocalClickOnRelease], which a setting is, it acts when the button is let go over the
 * element it went down on instead: a value is not changed by a press that was a slip, as long
 * as the pointer is moved off before letting go.
 *
 * Handled clicks are consumed: an element underneath never sees them.
 *
 * What can be clicked can be reached from the [keyboard] too, see [KeyboardFocus]: with the
 * focus on it, Enter or Space is a left click and Shift with either a right click. Where there
 * is nothing for a left click to do, the plain keys do what the right click does. Turn it off
 * for what is clicked as a backdrop rather than as a thing.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.mouseClickable(
    enabled: Boolean = true,
    keyboard: Boolean = true,
    onRightClick: (() -> Unit)? = null,
    onMiddleClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
): Modifier =
    if (!enabled) this
    else composed {
        val onRelease = LocalClickOnRelease.current
        val actionFor by rememberUpdatedState { button: PointerButton? ->
            when (button) {
                PointerButton.Primary -> onClick
                PointerButton.Secondary -> onRightClick
                PointerButton.Tertiary -> onMiddleClick
                else -> null
            }
        }
        val keys = if (!keyboard) Modifier else Modifier.keyboardFocusable { event ->
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
                    Click.pressedAt = System.nanoTime()
                    if (!onRelease) {
                        actionFor(button)?.invoke()
                        continue
                    }

                    // Whatever the pointer does in between, it is where the button comes up that counts
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()
                        // A press of another button means this one's release was never seen
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

/** Whether clicks on what is inside act when the button is let go rather than when it goes down, see [mouseClickable] */
val LocalClickOnRelease = compositionLocalOf { false }

/** The click being handled, for what has to tell it from the press that began it */
internal object Click {
    /** When the button went down for the click whose action runs now, by [System.nanoTime] */
    var pressedAt = 0L
}

/**
 * Calls [onPress] the moment [button] goes down on the element, with where the pointer is inside
 * it. For when the position matters, such as opening a context menu under the pointer.
 */
fun Modifier.onMousePress(button: PointerButton, onPress: (position: Offset) -> Unit): Modifier =
    onPress { pressed, position ->
        if (pressed == button) onPress(position)
        pressed == button
    }

/**
 * Offers every press on the element that nothing inside it took to [handle], which returns
 * whether the press was for it; if so, nothing further out gets to see it.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.onPress(handle: (button: PointerButton?, position: Offset) -> Boolean): Modifier = composed {
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

/**
 * Swallows presses that nothing inside the element handled, so clicking the bare part of a
 * window is not mistaken for clicking whatever lies behind it.
 */
fun Modifier.consumeClicks(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Press) event.changes.forEach { it.consume() }
        }
    }
}

/** Calls [onDrag] with the distance the pointer moved while the left button is held on the element */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.mouseDraggable(
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDrag: (Offset) -> Unit,
): Modifier = onDrag(
    matcher = PointerMatcher.mouse(PointerButton.Primary),
    onDragStart = { onDragStart() },
    onDragCancel = onDragEnd,
    onDragEnd = onDragEnd,
    onDrag = onDrag,
)

/**
 * For sliders and pickers: reports where the pointer is, relative to the element, from the moment
 * the left button goes down on it until it is released, wherever the pointer has wandered by then.
 */
fun Modifier.scrubbable(onScrub: (position: Offset, size: IntSize) -> Unit): Modifier = composed {
    val currentOnScrub by rememberUpdatedState(onScrub)
    pointerInput(Unit) {
        awaitEachGesture {
            // On a desktop only the left button counts as the first down of a gesture
            val down = awaitFirstDown()
            down.consume()
            currentOnScrub(down.position, size)
            drag(down.id) { change ->
                change.consume()
                currentOnScrub(change.position, size)
            }
        }
    }
}

/**
 * Scrolls [state] with the mouse wheel, in place of the scrolling a list comes with, which
 * moves at one speed and is over in a tenth of a second. Here every notch sets the content off
 * fast and lets it glide to rest, and further notches add to the distance still to go.
 *
 * Put it on the element around the list. A notch the list cannot follow, being at its end, is
 * left for whatever scrolls outside of it.
 */
fun Modifier.wheelScrollable(state: ScrollableState): Modifier = composed {
    val scope = rememberCoroutineScope()
    val notch = with(LocalDensity.current) { WheelStep.toPx() }
    val glide = remember { WheelGlide() }
    pointerInput(state, notch) {
        awaitPointerEventScope {
            while (true) {
                // On the way down, so the list's own wheel handling never gets to see the notch
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Scroll) continue
                val change = event.changes.first()
                val distance = change.scrollDelta.y * notch
                val canFollow = if (distance > 0) state.canScrollForward else state.canScrollBackward
                if (distance == 0f || !canFollow || change.isConsumed) continue
                change.consume()

                // What is left of the glide in progress is added to, unless the wheel turned round
                val total = distance + if (glide.remaining * distance > 0) glide.remaining else 0f
                glide.job?.cancel()
                glide.job = scope.launch {
                    var travelled = 0f
                    glide.remaining = total
                    state.scroll(MutatePriority.UserInput) {
                        animate(0f, total, animationSpec = Glide) { value, _ ->
                            scrollBy(value - travelled)
                            travelled = value
                            glide.remaining = total - value
                        }
                    }
                    glide.remaining = 0f
                }
            }
        }
    }
}

/** The glide a list is in: how far it still has to go, and the animation taking it there */
private class WheelGlide {
    var remaining = 0f
    var job: Job? = null
}

/** How far one line of the wheel takes a list; a notch is three lines, so three rows */
private val WheelStep = 14.dp

/** Off fast and a long time settling: most of the distance is covered in the first third */
private val Glide = tween<Float>(durationMillis = 520, easing = EaseOutQuint)

/** Fills the element with [color] while the pointer is over it */
fun Modifier.hoverHighlight(color: Color): Modifier = composed {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    hoverable(interactions).background(if (hovered) color else Color.Transparent)
}
