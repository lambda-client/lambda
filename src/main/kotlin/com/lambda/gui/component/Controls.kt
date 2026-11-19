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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lambda.gui.theme.ContentColor
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.LocalContentColor
import com.lambda.gui.theme.LocalRowHeight
import com.lambda.gui.theme.Look
import com.lambda.gui.theme.Radius
import com.lambda.gui.theme.Text
import com.lambda.gui.theme.Type

/**
 * How tall a module, a row of settings or a control is at least, so stacked ones line up. Set in
 * the style as its element height; by default a line of text and a hair of room.
 */
val RowHeight: Dp
    @Composable @ReadOnlyComposable get() = LocalRowHeight.current

private const val TOGGLE_MILLIS = 150

/**
 * A button. One that is [prominent] is filled like the header of a category rather than like a
 * control: for the one that leads somewhere else, of which a screen has one at most.
 */
@Composable
fun Button(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    prominent: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LambdaTheme.colors
    val fill = if (prominent) colors.header else colors.control
    val text = if (prominent) colors.onHeader else colors.onControl
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = RowHeight + 2.dp)
            .clip(RoundedCornerShape(Radius.Control))
            .background(fill)
            .hoverHighlight(text.copy(alpha = if (enabled) 0.12f else 0f))
            .mouseClickable(enabled, onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(label, color = if (enabled) text else colors.onSurfaceVariant, maxLines = 1)
    }
}

/** The box of a checkbox, or the tick in front of a checked menu item */
@Composable
fun Checkmark(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = LambdaTheme.colors
    Canvas(
        modifier
            .size(9.dp)
            .clip(RoundedCornerShape(Radius.Box))
            // Empty, it is the grey of a slider's track, which stands out on a panel without an outline
            .background(if (checked) colors.primary else colors.track)
    ) {
        if (!checked) return@Canvas
        val side = size.minDimension
        drawPath(
            path = Path().apply {
                moveTo(side * 0.22f, side * 0.52f)
                lineTo(side * 0.42f, side * 0.72f)
                lineTo(side * 0.78f, side * 0.28f)
            },
            color = colors.onPrimary,
            style = Stroke(side * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * Three dots in a row: what a chip ends in when clicking it opens a window to edit its value
 * in, where a [Chevron] would point at a list that is not going to drop out of it.
 */
@Composable
fun Ellipsis(modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    Canvas(modifier.size(7.dp, 5.dp)) {
        val dot = 1.dp.toPx()
        val top = (size.height - dot) / 2
        repeat(3) { drawRect(color, Offset(it * (size.width - dot) / 2, top), Size(dot, dot)) }
    }
}

/** A small triangle pointing right, turned by [degrees]: 90 points it down, -90 up */
@Composable
fun Chevron(degrees: Float, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    Canvas(modifier.size(5.dp).rotate(degrees)) {
        drawPath(
            path = Path().apply {
                moveTo(size.width * 0.2f, size.height * 0.1f)
                lineTo(size.width * 0.9f, size.height * 0.5f)
                lineTo(size.width * 0.2f, size.height * 0.9f)
                close()
            },
            color = color
        )
    }
}

/**
 * The [TextInput]s that hold the keyboard focus, one at most in the interface and in each popup
 * over it. While any does, keys are typed into it, so nothing else may act on them: no keybind,
 * and no walking around with the GUI open.
 */
internal object TextFocus {
    private val fields = mutableListOf<Field>()

    val isTyping get() = fields.isNotEmpty()

    /** A press at [position], in scene pixels, takes the focus from every field it is not on */
    fun press(position: Offset) = fields.filter { position !in it.bounds }.forEach { it.release() }

    fun add(field: Field) = fields.add(field)
    fun remove(field: Field) = fields.remove(field)

    class Field(val release: () -> Unit) {
        /** Where the field is, in scene pixels */
        var bounds = Rect.Zero
    }
}

/**
 * A single or multi line text field.
 *
 * The text being edited is kept here and [onValueChange] reports every edit, so [value] may lag
 * behind or disagree (a setting can refuse or correct what was typed) without the caret jumping:
 * the field only adopts [value] when it changes from outside.
 *
 * @param focused take the keyboard focus as soon as the field appears
 * @param onDone called when the user is finished with the field: on Enter, Escape or loss of focus
 */
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
    // Kept for as long as it has the focus, however it loses it, even by leaving the screen
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
        singleLine = singleLine,
        textStyle = style.copy(color = colors.onControl),
        cursorBrush = SolidColor(colors.onControl),
        modifier = modifier
            .heightIn(min = RowHeight)
            .clip(RoundedCornerShape(Radius.Control))
            .background(colors.input)
            .onGloballyPositioned { typing.bounds = it.boundsInWindow() }
            .focusRequester(focus)
            .onFocusChanged {
                hasFocus = it.isFocused
                if (it.isFocused) hadFocus = true
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

/**
 * A [TextInput] for a number. What is typed is kept as typed, so "1." is not rewritten to "1.0"
 * mid-keystroke; it gives way to [value] only when that changes to a different number.
 */
@Composable
fun NumberInput(
    value: Number,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    onDone: (() -> Unit)? = null,
) {
    var text by remember { mutableStateOf(value.toString()) }
    var adopted by remember { mutableStateOf(value) }
    if (value != adopted) {
        adopted = value
        if (text.toDoubleOrNull() != value.toDouble()) text = value.toString()
    }
    TextInput(
        value = text,
        onValueChange = {
            text = it
            it.toDoubleOrNull()?.let(onValueChange)
        },
        modifier = modifier,
        focused = focused,
        onDone = onDone,
    )
}

/**
 * A column that scrolls when it is too tall, with a slim scrollbar while it does. Pass a
 * [scroll] state to know from outside whether, and how far, it can.
 */
@Composable
fun ScrollColumn(
    modifier: Modifier = Modifier,
    scroll: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.wheelScrollable(scroll)) {
        Column(Modifier.verticalScroll(scroll), content = content)
        if (scroll.maxValue > 0) Scrollbar(rememberScrollbarAdapter(scroll), Modifier.matchParentSize())
    }
}

/**
 * The slim bar along the right edge of [modifier]'s area that every scrolling list shows, and
 * that can be dragged. Show it only while there is something to scroll.
 */
@Composable
fun Scrollbar(adapter: ScrollbarAdapter, modifier: Modifier) {
    val colors = LambdaTheme.colors
    Box(modifier) {
        VerticalScrollbar(
            adapter = adapter,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            style = ScrollbarStyle(
                minimalHeight = 12.dp,
                thickness = 2.dp,
                shape = RoundedCornerShape(1.dp),
                hoverDurationMillis = TOGGLE_MILLIS,
                unhoverColor = colors.onSurfaceVariant.copy(alpha = 0.5f),
                hoverColor = colors.onSurface
            )
        )
    }
}

/** Content that unfolds vertically, the way every collapsible part of the interface opens */
@Composable
fun Expandable(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(tween(TOGGLE_MILLIS)) + fadeIn(tween(TOGGLE_MILLIS)),
        exit = shrinkVertically(tween(TOGGLE_MILLIS)) + fadeOut(tween(TOGGLE_MILLIS)),
    ) { content() }
}

/**
 * A titled section that is collapsed until its header is clicked. [tooltip] explains the header.
 *
 * The header is a raised strip. Unfolded, a bar in the accent colour runs from the top of the
 * header down its edge to the end of what it holds, which is set in beside the bar: header and
 * content read as one bracket, also when a section sits inside another.
 *
 * @param revealed unfolds the section, for when something in it is to be pointed at
 */
@Composable
fun Section(
    title: String,
    suffix: String? = null,
    tooltip: String = "",
    initiallyExpanded: Boolean = false,
    revealed: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LambdaTheme.colors
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    LaunchedEffect(revealed) {
        if (revealed) expanded = true
    }
    val turn by animateFloatAsState(if (expanded) 90f else 0f, tween(TOGGLE_MILLIS))
    val rowHeight = RowHeight

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 1.dp)
            .drawWithContent {
                drawContent()
                // Taller than its header, the section has something unfolded, or is unfolding
                if (size.height > rowHeight.toPx() + 1f) {
                    drawRect(colors.primary, size = Size(SectionBar.toPx(), size.height))
                }
            }
    ) {
        Tooltip(tooltip, Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = RowHeight)
                    .clip(RoundedCornerShape(Radius.Control))
                    .background(colors.raised)
                    .mouseClickable { expanded = !expanded }
                    .padding(horizontal = 4.dp)
            ) {
                Chevron(turn, color = colors.onSurface)
                Text(title, color = colors.onSurface, maxLines = 1)
                if (suffix != null) Text(suffix, color = colors.onSurfaceVariant, style = Type.label, maxLines = 1)
            }
        }
        Expandable(expanded) {
            Column(Modifier.fillMaxWidth().padding(start = SectionIndent, top = 1.dp), content = content)
        }
    }
}

private val SectionBar = 1.dp

/** How far what a section holds is set in, clear of the bar */
private val SectionIndent get() = Look.groupIndent

/**
 * A value shown at the end of a setting's row as something to click: the chosen option of a
 * dropdown, the size of a collection. As tall as a row, whatever it holds.
 */
@Composable
fun Chip(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val colors = LambdaTheme.colors
    ContentColor(colors.onControl) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = modifier
                .height(RowHeight)
                .clip(RoundedCornerShape(Radius.Control))
                .background(colors.control)
                .hoverHighlight(colors.onControl.copy(alpha = 0.12f))
                .mouseClickable(onClick = onClick)
                .padding(horizontal = 4.dp),
            content = content
        )
    }
}

/**
 * A row with [label] and, at its end, the [selected] one of [options] on a [Chip]. Clicking the
 * chip lists the options in a menu over the interface, so picking one moves nothing around it.
 */
@Composable
fun <T> Dropdown(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    val menu = remember { MenuState() }
    var chip by remember { mutableStateOf(IntSize.Zero) }
    LabeledRow(label, modifier) {
        Box(Modifier.onSizeChanged { chip = it }) {
            // A choice too long for the room the chip is given slides along under the pointer
            Chip(onClick = menu::toggle) {
                SlidingText(optionLabel(selected), Modifier.weight(1f, fill = false))
                Chevron(90f)
            }
            // Drops out from under the chip, as wide as it, and folds all the way back up into it
            Menu(menu.isOpen, onDismiss = menu::dismiss, unfoldFrom = IntSize(chip.width, 0)) {
                options.forEach { option ->
                    MenuItem(optionLabel(option), selected = option == selected) {
                        if (option != selected) onSelect(option)
                    }
                }
            }
        }
    }
}

/**
 * A row with [label] at its start and, at its end, whatever edits the value it names.
 *
 * With room for both, each is as wide as it likes and the room left over lies between them.
 * Without, as in the width of a window when settings drop down under their module, neither is
 * let crowd the other out: the editor gives way first, down to three fifths of the row, and the
 * label has what is left. What does not fit of either slides along under the pointer.
 */
@Composable
fun LabeledRow(label: String, modifier: Modifier = Modifier, editor: @Composable RowScope.() -> Unit) {
    Layout(
        content = {
            Text(label, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LabelGap), content = editor)
        },
        modifier = modifier.fillMaxWidth().heightIn(min = RowHeight),
    ) { (name, edited), constraints ->
        val gap = LabelGap.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val room = constraints.maxWidth
        val wanted = edited.maxIntrinsicWidth(constraints.maxHeight)
        // Asked how wide it would like to be, which is what sizes a panel: as wide as both
        val editorWidth =
            if (!constraints.hasBoundedWidth) wanted
            else wanted.coerceAtMost(maxOf(room - name.maxIntrinsicWidth(constraints.maxHeight) - gap, room * 3 / 5))
        val editor = edited.measure(loose.copy(maxWidth = editorWidth.coerceAtLeast(0)))
        val nameRoom = if (constraints.hasBoundedWidth) (room - editor.width - gap).coerceAtLeast(0) else Constraints.Infinity
        val text = name.measure(loose.copy(maxWidth = nameRoom))

        val width = if (constraints.hasBoundedWidth) room else text.width + gap + editor.width
        val height = maxOf(text.height, editor.height, constraints.minHeight)
        layout(width, height) {
            text.place(0, (height - text.height) / 2)
            editor.place(width - editor.width, (height - editor.height) / 2)
        }
    }
}

/** Room between a label and what edits its value, and between the parts of that */
private val LabelGap = 3.dp

/** Fills whatever space is left with nothing, centring [text] in it: what a list shows when empty */
@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = LambdaTheme.colors.onSurfaceVariant, style = Type.label)
    }
}
