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
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.areAnyPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.lambda.gui.core.GameClipboard
import com.lambda.gui.core.LocalShown
import com.lambda.gui.core.Press
import com.lambda.gui.core.awaitFrames
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.Look
import com.lambda.gui.theme.Motion
import com.lambda.gui.theme.Type
import com.lambda.module.modules.client.Style
import kotlin.math.max

@OptIn(ExperimentalComposeUiApi::class)
private val FLOATING =
    PopupProperties(
        focusable = true,
        dismissOnBackPress = true,
        dismissOnClickOutside = true,
        clippingEnabled = true,
        usePlatformDefaultWidth = false,
        usePlatformInsets = true,
        consumePointerInputOutside = false,
    )

private val SLACK = 4.dp

@Composable
private fun ScenePopup(
    popupPositionProvider: PopupPositionProvider,
    onDismissRequest: (() -> Unit)? = null,
    properties: PopupProperties = PopupProperties(),
    onKeyEvent: ((KeyEvent) -> Boolean)? = null,
    content: @Composable () -> Unit,
) =
    Popup(popupPositionProvider, onDismissRequest, properties, onKeyEvent = onKeyEvent) {
        CompositionLocalProvider(LocalClipboard provides GameClipboard, content = content)
    }

enum class MenuPlacement {
    Below,

    Beside,
}

@Stable
class MenuState {
    var isOpen by mutableStateOf(false)
        private set
    private var dismissedBy = -1L

    fun dismiss() {
        isOpen = false
        dismissedBy = Press.id
    }

    fun toggle() {
        if (dismissedBy != Press.id) isOpen = true
    }
}

@Composable
fun Menu(
    open: Boolean,
    onDismiss: () -> Unit,
    placement: MenuPlacement = MenuPlacement.Below,
    unfoldFrom: IntSize = IntSize.Zero,
    attached: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    MenuPopup(
        open,
        remember(placement) { UnfoldingPosition(placement::place) },
        onDismiss,
        unfoldFrom,
        attached,
        content
    )
}

private fun MenuPlacement.place(anchor: IntRect, window: IntSize, popup: IntSize) =
    when (this) {
        MenuPlacement.Below ->
            IntOffset(
                anchor.left,
                if (anchor.bottom + popup.height <= window.height ||
                    anchor.top - popup.height < 0
                ) {
                    anchor.bottom
                }
                else anchor.top - popup.height
            )
        MenuPlacement.Beside ->
            IntOffset(
                if (anchor.right + popup.width <= window.width) anchor.right
                else anchor.left - popup.width,
                anchor.top
            )
    }

@Composable
fun Popout(
    open: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val position = remember { UnfoldingPosition(MenuPlacement.Beside::place) }
    FloatingPanel(
        open,
        position,
        onDismiss,
        RoundedCornerShape(Look.panelRounding)
    ) { panel ->
        Column(panel.padding(3.dp), content = content)
    }
}

@Composable
fun ContextMenu(
    open: Boolean,
    position: IntOffset,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) =
    MenuPopup(
        open,
        remember(position) { UnfoldingPosition { _, _, _ -> position } },
        onDismiss,
        IntSize.Zero,
        false,
        content
    )

@Composable
fun <T : Any> rememberLast(value: T?): T? {
    val last = remember { LastValue<T>() }
    if (value != null) last.value = value
    return last.value
}

private class LastValue<T : Any> {
    var value: T? = null
}

private val LocalMenuDismiss = staticCompositionLocalOf<(() -> Unit)?> { null }

private val LocalMenuOpen = compositionLocalOf { true }

@Composable
private fun MenuPopup(
    open: Boolean,
    position: UnfoldingPosition,
    onDismiss: () -> Unit,
    unfoldFrom: IntSize,
    attached: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val open = open && LocalMenuOpen.current
    val dismissAll = LocalMenuDismiss.current ?: onDismiss
    val screenHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val shape =
        when {
            !attached -> RoundedCornerShape(Look.menuRounding)
            position.upwards -> RoundedCornerShape(topStart = Look.menuRounding, topEnd = Look.menuRounding)
            else -> RoundedCornerShape(bottomStart = Look.menuRounding, bottomEnd = Look.menuRounding)
        }
    FloatingPanel(open, position, onDismiss, shape, unfoldFrom) { panel ->
        CompositionLocalProvider(LocalMenuDismiss provides dismissAll, LocalMenuOpen provides open) {
            ScrollColumn(
                modifier = Modifier
                    .heightIn(max = screenHeight)
                    .width(IntrinsicSize.Max)
                    .widthIn(min = maxOf(80.dp, with(LocalDensity.current) { unfoldFrom.width.toDp() }))
                    .then(panel)
                    .clip(shape),
                content = content
            )
        }
    }
}

class PopupHost {
    var closing by mutableStateOf(false)

    var bounds by mutableStateOf(Rect.Zero)
        private set

    var full = Rect.Zero
        private set

    fun place(bounds: Rect) {
        this.bounds = bounds
        if (!closing) full = bounds
    }
}

val LocalPopupHost = compositionLocalOf<PopupHost?> { null }

private fun Modifier.swallowedBy(host: PopupHost?): Modifier =
    if (host == null) this
    else composed {
        var origin by remember { mutableStateOf(Offset.Zero) }
        onGloballyPositioned { origin = it.positionInWindow() }.drawWithContent {
            if (!host.closing) return@drawWithContent drawContent()
            val full = host.full
            val left = host.bounds
            val slack = SLACK.toPx()
            val dx =
                when {
                    origin.x >= full.right - slack -> left.right - full.right
                    origin.x + size.width <= full.left + slack -> left.left - full.left
                    else -> null
                }
            val dy =
                when {
                    origin.y >= full.bottom - slack -> left.bottom - full.bottom
                    origin.y + size.height <= full.top + slack -> left.top - full.top
                    else -> null
                }
            val far = 10_000f
            clipRect(
                if (dx == null) left.left - origin.x else -far,
                if (dy == null) left.top - origin.y else -far,
                if (dx == null) left.right - origin.x else far,
                if (dy == null) left.bottom - origin.y else far,
            ) {
                translate(dx ?: 0f, dy ?: 0f) { this@drawWithContent.drawContent() }
            }
        }
    }

@Composable
private fun FloatingPanel(
    open: Boolean,
    position: UnfoldingPosition,
    onDismiss: () -> Unit,
    shape: Shape,
    unfoldFrom: IntSize = IntSize.Zero,
    content: @Composable (panel: Modifier) -> Unit,
) {
    if (!LocalShown.current) {
        if (open) SideEffect(onDismiss)
        return
    }
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = open
    if (!open && visibility.isIdle && !visibility.currentState) return

    val host = LocalPopupHost.current
    if (host != null && host.closing && open) SideEffect(onDismiss)
    val own = remember { PopupHost() }
    SideEffect { own.closing = !open }

    val colors = LambdaTheme.colors
    ScenePopup(
        popupPositionProvider = position,
        onDismissRequest = { if (open) onDismiss() },
        properties = FLOATING,
        onKeyEvent = { open && it.dismissOnEscape(onDismiss) },
    ) {
        if (open) KeyboardFocus.Layer()
        ContentColor(colors.onSurface) {
            AnimatedVisibility(
                visibleState = visibility,
                modifier = Modifier
                    .swallowedBy(host)
                    .onGloballyPositioned { own.place(it.boundsInWindow()) }
                    .dropShadow(shape, close = true)
                    .window(colors.panel, colors.border, shape),
                enter = expandIn(Motion.panels, position.corner) { it.coerceAtMost(unfoldFrom) },
                exit = shrinkOut(Motion.panels, position.corner) { it.coerceAtMost(unfoldFrom) },
            ) {
                CompositionLocalProvider(LocalPopupHost provides own) {
                    content(Modifier.onSizeChanged { position.unfolded = it })
                }
            }
        }
    }
}

private class UnfoldingPosition(
    private val position: (anchor: IntRect, window: IntSize, popup: IntSize) -> IntOffset,
) : PopupPositionProvider {
    var unfolded = IntSize.Zero

    private var leftwards by mutableStateOf(false)

    var upwards by mutableStateOf(false)
        private set

    val corner
        get() =
            when {
                upwards ->
                    if (leftwards) Alignment.BottomEnd
                    else Alignment.BottomStart
                else ->
                    if (leftwards) Alignment.TopEnd
                    else Alignment.TopStart
            }

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val full =
            IntSize(
                max(unfolded.width, popupContentSize.width),
                max(unfolded.height, popupContentSize.height)
            )
        val wanted = position(anchorBounds, windowSize, full)
        leftwards = wanted.x + full.width <= anchorBounds.left
        upwards = wanted.y + full.height <= anchorBounds.top
        val x = wanted.x.coerceIn(0, (windowSize.width - full.width).coerceAtLeast(0))
        val y = wanted.y.coerceIn(0, (windowSize.height - full.height).coerceAtLeast(0))
        return IntOffset(
            if (leftwards) x + full.width - popupContentSize.width else x,
            if (upwards) y + full.height - popupContentSize.height else y
        )
    }
}

@Composable
fun Suggestions(
    open: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!LocalShown.current) {
        if (open) SideEffect(onDismiss)
        return
    }
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = open
    if (!open && visibility.isIdle && !visibility.currentState) return

    val colors = LambdaTheme.colors
    val shape = RoundedCornerShape(Look.menuRounding)
    val screenHeight =
        with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    ScenePopup(
        popupPositionProvider = remember {
            positionProvider { anchor, window, popup ->
                val centred = anchor.left + (anchor.width - popup.width) / 2
                IntOffset(
                    centred.coerceIn(0, (window.width - popup.width).coerceAtLeast(0)),
                    anchor.bottom
                )
            }
        },
        onDismissRequest = { if (open) onDismiss() },
        properties = PopupProperties(focusable = false),
    ) {
        ContentColor(colors.onSurface) {
            AnimatedVisibility(
                visibleState = visibility,
                modifier = Modifier
                    .dropShadow(shape)
                    .window(colors.panel, colors.border, shape),
                enter = expandVertically(Motion.panels, Alignment.Top),
                exit = shrinkVertically(Motion.panels, Alignment.Top),
            ) {
                ScrollColumn(
                    modifier = Modifier
                        .heightIn(max = screenHeight * 0.6f)
                        .width(IntrinsicSize.Max)
                        .animateContentSize(Motion.panels),
                    content = content
                )
            }
        }
    }
}

@Composable
fun MenuItem(
    label: String,
    enabled: Boolean = true,
    checked: Boolean? = null,
    selected: Boolean = false,
    hint: String? = null,
    onClick: () -> Unit,
) {
    val colors = LambdaTheme.colors
    val dismiss = LocalMenuDismiss.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) colors.controlSelected else Color.Transparent)
            .then(
                if (!enabled) Modifier
                else Modifier.hoverHighlight(colors.controlSelected).mouseClickable {
                    onClick()
                    if (checked == null) dismiss?.invoke()
                }
            )
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        if (checked != null) Checkmark(checked, Modifier.padding(end = 3.dp))
        val color =
            when {
                !enabled -> colors.onSurfaceVariant
                selected -> colors.secondary
                else -> colors.onSurface
            }
        Text(label, Modifier.weight(1f), color, maxLines = 1)
        if (hint != null) {
            Text(
                hint,
                Modifier.padding(start = 12.dp),
                colors.onSurfaceVariant,
                Type.label,
                maxLines = 1
            )
        }
    }
}

@Composable
fun Submenu(
    label: String,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LambdaTheme.colors
    val menu = remember { MenuState() }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (enabled) {
                        Modifier
                            .hoverHighlight(colors.controlSelected)
                            .mouseClickable(onClick = menu::toggle)
                    }
                    else Modifier
                )
                .padding(horizontal = 5.dp, vertical = 1.dp)
        ) {
            Text(
                label,
                Modifier.weight(1f),
                if (enabled) colors.onSurface else colors.onSurfaceVariant,
                maxLines = 1
            )
            Chevron(0f, Modifier.padding(start = 12.dp), colors.onSurfaceVariant)
        }
        Menu(
            menu.isOpen,
            onDismiss = menu::dismiss,
            MenuPlacement.Beside,
            content = content
        )
    }
}

@Composable
fun MenuLabel(label: String) =
    Text(
        label,
        Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        LambdaTheme.colors.onSurfaceVariant,
        maxLines = 1
    )

@Composable
fun MenuHelp(text: String, label: String = "Help") {
    val colors = LambdaTheme.colors
    Tooltip(text, Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .hoverHighlight(colors.raised)
                .padding(horizontal = 5.dp, vertical = 1.dp)
        ) {
            Text(label, Modifier.weight(1f), colors.onSurfaceVariant, maxLines = 1)
            Text(
                "?",
                Modifier.padding(start = 12.dp),
                colors.onSurfaceVariant,
                Type.label,
                maxLines = 1
            )
        }
    }
}

@Composable
fun MenuDivider() =
    Spacer(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 3.dp, vertical = 1.dp)
            .height(1.dp)
            .background(LambdaTheme.colors.border)
    )

@Composable
fun Dialog(onDismiss: (() -> Unit)?, content: @Composable BoxScope.() -> Unit) {
    if (!LocalShown.current) {
        if (onDismiss != null) SideEffect(onDismiss)
        return
    }
    val colors = LambdaTheme.colors
    ScenePopup(
        popupPositionProvider = remember {
            positionProvider { _, _, _ -> IntOffset.Zero }
        },
        properties = PopupProperties(focusable = true),
        onKeyEvent = { onDismiss != null && it.dismissOnEscape(onDismiss) },
    ) {
        KeyboardFocus.Layer()
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .glass(Color.Black.copy(alpha = 0.35f), RectangleShape)
                .mouseClickable(keyboard = false) { onDismiss?.invoke() }
        ) {
            ContentColor(colors.onSurface) {
                Box(
                    modifier = Modifier
                        .window(
                            colors.panel,
                            colors.border,
                            RoundedCornerShape(Look.panelRounding),
                            floating = true
                        )
                        .padding(6.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
fun Tooltip(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (text.isBlank()) {
        Box(modifier) { content() }
        return
    }
    val colors = LambdaTheme.colors
    var hovered by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    LaunchedEffect(hovered) {
        if (!hovered) return@LaunchedEffect
        awaitFrames(Style.tooltipDelay.toLong())
        visible = true
    }

    Box(
        modifier
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        when (event.type) {
                            PointerEventType.Enter -> hovered = !event.buttons.areAnyPressed
                            PointerEventType.Exit, PointerEventType.Press -> {
                                hovered = false
                                visible = false
                            }
                        }
                        if (!visible) pointer = event.changes.first().position
                    }
                }
            }
    ) {
        content()
        if (visible && hovered) {
            val below = with(LocalDensity.current) { 16.dp.roundToPx() }
            ScenePopup(
                popupPositionProvider = remember(pointer, bounds) {
                    positionProvider { _, window, popup ->
                        val at = (bounds.topLeft + pointer).round() + IntOffset(0, below)
                        IntOffset(
                            at.x.coerceIn(0, (window.width - popup.width).coerceAtLeast(0)),
                            at.y.coerceIn(0, (window.height - popup.height).coerceAtLeast(0))
                        )
                    }
                },
                properties = PopupProperties(focusable = false),
            ) {
                Text(
                    text = text,
                    color = colors.onSurface,
                    style = Type.label,
                    modifier = Modifier
                        .widthIn(max = 220.dp)
                        .window(colors.panel, colors.border, RoundedCornerShape(Look.menuRounding))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}

private fun KeyEvent.dismissOnEscape(onDismiss: () -> Unit): Boolean {
    if (key != Key.Escape) return false
    if (type == KeyEventType.KeyDown) onDismiss()
    return true
}

private fun positionProvider(
    position: (anchor: IntRect, window: IntSize, popup: IntSize) -> IntOffset
) =
    object : PopupPositionProvider {
        override fun calculatePosition(
            anchorBounds: IntRect,
            windowSize: IntSize,
            layoutDirection: LayoutDirection,
            popupContentSize: IntSize,
        ) = position(anchorBounds, windowSize, popupContentSize)
    }

private fun IntSize.coerceAtMost(other: IntSize) = IntSize(minOf(width, other.width), minOf(height, other.height))
