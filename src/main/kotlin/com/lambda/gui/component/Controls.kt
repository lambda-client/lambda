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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.LocalContentColor
import com.lambda.gui.theme.Look

private const val TOGGLE_MILLIS = 150
private val SECTION_BAR = 1.dp
private val LABEL_GAP = 3.dp
private val LEADING_GAP = 3.dp

@Composable
fun Button(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    prominent: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LambdaTheme.colors
    val fill =
        if (prominent) colors.header
        else colors.control
    val text =
        if (prominent) colors.onHeader
        else colors.onControl
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = Look.rowHeight + 2.dp)
            .clip(RoundedCornerShape(Look.controlRounding))
            .background(fill)
            .hoverHighlight(text.copy(alpha = if (enabled) 0.12f else 0f))
            .mouseClickable(enabled, onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(label, color = if (enabled) text else colors.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
fun Checkmark(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = LambdaTheme.colors
    Canvas(
        modifier
            .size(9.dp)
            .clip(RoundedCornerShape(Look.boxRounding))
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
            color = colors.onControl,
            style = Stroke(side * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun Ellipsis(modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    Canvas(modifier.size(7.dp, 5.dp)) {
        val dot = 1.dp.toPx()
        val top = (size.height - dot) / 2
        repeat(3) {
            drawRect(color, Offset(it * (size.width - dot) / 2, top), Size(dot, dot))
        }
    }
}

@Composable
fun Chevron(
    degrees: Float,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current
) {
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

@Composable
fun Expandable(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(tween(TOGGLE_MILLIS)) + fadeIn(tween(TOGGLE_MILLIS)),
        exit = shrinkVertically(tween(TOGGLE_MILLIS)) + fadeOut(tween(TOGGLE_MILLIS)),
    ) { content() }
}

@Composable
fun Section(
    title: String,
    revealed: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LambdaTheme.colors
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(revealed) {
        if (revealed) expanded = true
    }
    val turn by animateFloatAsState(if (expanded) 90f else 0f, tween(TOGGLE_MILLIS))
    val rowHeight = Look.rowHeight

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 1.dp)
            .drawWithContent {
                drawContent()
                if (size.height > rowHeight.toPx() + 1f) {
                    drawRect(colors.secondary, size = Size(SECTION_BAR.toPx(), size.height))
                }
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Look.rowHeight)
                .clip(RoundedCornerShape(Look.controlRounding))
                .background(colors.raised)
                .mouseClickable { expanded = !expanded }
                .padding(horizontal = 4.dp)
        ) {
            Chevron(turn, color = colors.onSurface)
            Text(title, color = colors.onSurface, maxLines = 1)
        }
        Expandable(expanded) {
            Column(
                Modifier.fillMaxWidth().padding(start = Look.groupIndent, top = 1.dp),
                content = content
            )
        }
    }
}

@Composable
fun Chip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val colors = LambdaTheme.colors
    ContentColor(colors.onControl) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = modifier
                .height(Look.rowHeight)
                .clip(RoundedCornerShape(Look.controlRounding))
                .background(colors.control)
                .hoverHighlight(colors.onControl.copy(alpha = 0.12f))
                .mouseClickable(onClick = onClick)
                .padding(horizontal = 4.dp),
            content = content
        )
    }
}

@Composable
fun <T> Dropdown(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    onOptionHover: (option: T?) -> Unit = {},
    onOptionClick: (option: T) -> Unit = {},
    onSelect: (T) -> Unit,
) {
    val menu = remember { MenuState() }
    var chip by remember { mutableStateOf(IntSize.Zero) }
    var hovered by remember { mutableStateOf<T?>(null) }
    val currentOnOptionHover by rememberUpdatedState(onOptionHover)
    LaunchedEffect(hovered) { currentOnOptionHover(hovered) }
    DisposableEffect(Unit) {
        onDispose { currentOnOptionHover(null) }
    }
    LabeledRow(label, modifier, leading) {
        Box(Modifier.onSizeChanged { chip = it }) {
            Chip(onClick = menu::toggle) {
                SlidingText(optionLabel(selected), Modifier.weight(1f, fill = false))
                Chevron(90f)
            }
            Menu(
                menu.isOpen,
                onDismiss = menu::dismiss,
                unfoldFrom = IntSize(chip.width, 0)
            ) {
                options.forEach { option ->
                    MenuItem(
                        optionLabel(option),
                        selected = option == selected,
                        onHover = {
                            if (it) hovered = option
                            else if (hovered == option) hovered = null
                        }
                    ) {
                        onOptionClick(option)
                        if (option != selected) onSelect(option)
                    }
                }
            }
        }
    }
}

@Composable
fun LabeledRow(
    label: String,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    editor: @Composable RowScope.() -> Unit
) {
    Layout(
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LEADING_GAP)
            ) {
                leading()
                Text(label, maxLines = 1)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LABEL_GAP),
                content = editor
            )
        },
        modifier = modifier.fillMaxWidth().heightIn(min = Look.rowHeight),
    ) { (name, edited), constraints ->
        val gap = LABEL_GAP.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val room = constraints.maxWidth
        val wanted = edited.maxIntrinsicWidth(constraints.maxHeight)
        val editorWidth =
            if (!constraints.hasBoundedWidth) wanted
            else {
                wanted.coerceAtMost(
                    maxOf(
                        room - name.maxIntrinsicWidth(constraints.maxHeight) - gap,
                        room * 3 / 5
                    )
                )
            }
        val editor = edited.measure(loose.copy(maxWidth = editorWidth.coerceAtLeast(0)))
        val nameRoom =
            if (constraints.hasBoundedWidth) (room - editor.width - gap).coerceAtLeast(0)
            else Constraints.Infinity
        val text = name.measure(loose.copy(maxWidth = nameRoom))

        val width =
            if (constraints.hasBoundedWidth) room
            else text.width + gap + editor.width
        val height = maxOf(text.height, editor.height, constraints.minHeight)
        layout(width, height) {
            text.place(0, (height - text.height) / 2)
            editor.place(width - editor.width, (height - editor.height) / 2)
        }
    }
}
