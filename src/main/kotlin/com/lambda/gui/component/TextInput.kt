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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.Look
import com.lambda.gui.theme.Type

internal object TextFocus {
    private val fields = mutableListOf<Field>()

    val isTyping get() = fields.isNotEmpty()

    fun press(position: Offset) = fields.filter { position !in it.bounds }.forEach { it.release() }

    fun add(field: Field) = fields.add(field)
    fun remove(field: Field) = fields.remove(field)

    class Field(val release: () -> Unit) {
        var bounds = Rect.Zero
    }
}

@Composable
fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    placeholder: String = "",
    style: TextStyle = Type.body,
    focused: Boolean = false,
    onDone: (() -> Unit)? = null,
) {
    val colors = LambdaTheme.colors
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    var adopted by remember { mutableStateOf(value) }
    if (value != adopted) {
        adopted = value
        if (value != field.text) field = TextFieldValue(value, TextRange(value.length))
    }

    val focus = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    var hasFocus by remember { mutableStateOf(false) }
    if (focused) LaunchedEffect(Unit) { focus.requestFocus() }
    val focusManager = LocalFocusManager.current
    val typing = remember(focusManager) { TextFocus.Field { focusManager.clearFocus() } }
    if (hasFocus) DisposableEffect(typing) {
        TextFocus.add(typing)
        onDispose { TextFocus.remove(typing) }
    }

    BasicTextField(
        value = field,
        onValueChange = {
            val edited = it.text != field.text
            field = it
            if (edited) onValueChange(it.text)
        },
        enabled = LocalKeyboardReachable.current,
        singleLine = singleLine,
        textStyle = style.copy(color = colors.onControl),
        cursorBrush = SolidColor(colors.onControl),
        modifier = modifier
            .heightIn(min = Look.rowHeight)
            .clip(RoundedCornerShape(Look.controlRounding))
            .background(colors.input)
            .onGloballyPositioned { typing.bounds = it.boundsInWindow() }
            .focusRequester(focus)
            .onFocusChanged {
                hasFocus = it.isFocused
                if (it.isFocused) hadFocus =
                    true
                    else if (hadFocus) onDone?.invoke()
            }
            .onPreviewKeyEvent { event ->
                if (onDone == null || (event.key != Key.Enter && event.key != Key.Escape)) false
                else {
                    if (event.type == KeyEventType.KeyDown) onDone()
                    true
                }
            }
            .padding(horizontal = 3.dp, vertical = 1.dp),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (field.text.isEmpty()) Text(placeholder, color = colors.onSurfaceVariant, style = style, maxLines = 1)
                innerTextField()
            }
        }
    )
}

@Composable
fun <T : Any> NumberInput(
    value: T,
    parse: (String) -> T?,
    onCommit: (T) -> Unit,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    onDone: () -> Unit = {},
) {
    var draft by remember { mutableStateOf<String?>(null) }
    fun finish(commit: Boolean) {
        val typed = draft
        draft = null
        if (commit && typed != null) parse(typed.trim())?.takeIf { it != value }?.let(onCommit)
        onDone()
    }
    TextInput(
        value = draft ?: value.toString(),
        onValueChange = { draft = it },
        modifier = modifier.onPreviewKeyEvent { event ->
            if (event.key != Key.Escape) false
            else {
                if (event.type == KeyEventType.KeyDown) finish(commit = false)
                true
            }
        },
        focused = focused,
        onDone = { finish(commit = true) },
    )
}
