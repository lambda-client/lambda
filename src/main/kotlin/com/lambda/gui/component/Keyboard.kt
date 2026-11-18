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

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.lambda.gui.theme.LambdaTheme

/**
 * How the interface is being driven. Everything that can be clicked can also be reached from
 * the keyboard: Tab and Shift with Tab step through it in order, the arrow keys move to what
 * lies in their direction, and Enter or Space is a click on whatever the focus is on, with
 * Shift a right click.
 *
 * The focus is only marked, and only acted on, while it is the keyboard that moves it. A mouse
 * press puts the mark away, and with it the chance of Enter toggling something that was left
 * focused out of sight; the next Tab or arrow key brings it back.
 */
internal object KeyboardFocus {
    var visible by mutableStateOf(false)

    /** The focus of the interface and of every window open over it that takes the keyboard, the innermost last */
    private val layers = ArrayList<FocusManager>()

    /** Moves the focus of whatever has the keyboard, and returns whether there was anywhere to move it */
    fun move(direction: FocusDirection) = layers.lastOrNull()?.moveFocus(direction) == true

    /**
     * Has the keys that move the focus move it here, among what this is called beside, for as
     * long as it is on screen. For the interface itself and for what opens over it and takes
     * the keyboard, a menu or a dialog: each of those has a focus of its own.
     */
    @Composable
    fun Layer() {
        val manager = LocalFocusManager.current
        DisposableEffect(manager) {
            layers += manager
            onDispose { layers -= manager }
        }
    }
}

/**
 * Makes the element a stop for the keyboard focus, outlined while it holds it. Every key that
 * goes down while it does is offered to [onKey], which returns whether the key was for it.
 */
fun Modifier.keyboardFocusable(onKey: (KeyEvent) -> Boolean): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val handle by rememberUpdatedState(onKey)
    val mark = LambdaTheme.colors.onSurface
    onFocusChanged { focused = it.isFocused }
        .onKeyEvent { it.type == KeyEventType.KeyDown && KeyboardFocus.visible && handle(it) }
        // Brings what it is on into view as well, in a list that scrolls
        .focusable()
        // In the colour of text, as what the search points at is lit: nothing else is outlined in it
        .drawWithContent {
            drawContent()
            if (!focused || !KeyboardFocus.visible) return@drawWithContent
            val width = FocusMark.toPx()
            drawRect(mark, Offset(width / 2, width / 2), Size(size.width - width, size.height - width), style = Stroke(width))
        }
}

/** Whether the key is one that clicks what the focus is on */
internal val KeyEvent.isActivation get() = key == Key.Enter || key == Key.NumPadEnter || key == Key.Spacebar

private val FocusMark = 1.dp
