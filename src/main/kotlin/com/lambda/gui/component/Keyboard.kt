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
import androidx.compose.runtime.compositionLocalOf
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

private val FOCUS_MARK = 1.dp

internal object KeyboardFocus {
    var visible by mutableStateOf(false)

    private val layers = ArrayList<FocusManager>()

    fun move(direction: FocusDirection) = layers.lastOrNull()?.moveFocus(direction) == true

    @Composable
    fun Layer() {
        val manager = LocalFocusManager.current
        DisposableEffect(manager) {
            layers += manager
            onDispose { layers -= manager }
        }
    }
}

fun Modifier.keyboardFocusable(onKey: (KeyEvent) -> Boolean): Modifier = composed {
    if (!LocalKeyboardReachable.current) return@composed Modifier
    var focused by remember { mutableStateOf(false) }
    val handle by rememberUpdatedState(onKey)
    val mark = LambdaTheme.colors.onSurface
    onFocusChanged { focused = it.isFocused }
        .onKeyEvent { it.type == KeyEventType.KeyDown && KeyboardFocus.visible && handle(it) }
        .focusable()
        .drawWithContent {
            drawContent()
            if (!focused || !KeyboardFocus.visible) return@drawWithContent
            val width = FOCUS_MARK.toPx()
            drawRect(mark, Offset(width / 2, width / 2), Size(size.width - width, size.height - width), style = Stroke(width))
        }
}

val LocalKeyboardReachable = compositionLocalOf { true }

internal val KeyEvent.isActivation get() = key == Key.Enter || key == Key.Spacebar
