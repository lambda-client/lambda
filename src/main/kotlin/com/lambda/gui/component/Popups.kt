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
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.animateContentSize
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
import com.lambda.gui.core.LocalShown
import com.lambda.gui.core.awaitFrames
import com.lambda.gui.theme.ContentColor
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.Motion
import com.lambda.gui.theme.Radius
import com.lambda.gui.theme.Text
import com.lambda.gui.theme.Type
import com.lambda.module.modules.client.Style
import kotlin.math.max

/** Where a [Menu] opens relative to the element it is declared in */
enum class MenuPlacement {
    /** Under the element, left edges aligned, or over it where the window ends first: a dropdown */
    Below,

    /** Beside the element, top edges aligned: a submenu */
    Beside,
}

/**
 * Whether a menu is open, kept by whatever opens it.
 *
 * A press outside a menu closes it and then goes on to what was pressed. When that is the
 * very thing that opens the menu, [toggle], whether called by that press or once the button is
 * let go, knows the menu was closed by the press of this same click and leaves it closed: clicking an open menu's
 * button shuts it rather than opening it again.
 */
@Stable
class MenuState {
    var isOpen by mutableStateOf(false)
        private set
    private var dismissedAt = Long.MIN_VALUE

    fun dismiss() {
        isOpen = false
        dismissedAt = System.nanoTime()
    }

    fun toggle() {
        if (dismissedAt < Click.pressedAt - SAME_PRESS_NANOS) isOpen = true
    }

    private companion object {
        /** The menu closes a moment before the press reaches what was pressed: this far apart they are one press */
        const val SAME_PRESS_NANOS = 50_000_000L
    }
}

/**
 * What every floating panel shares: it has the keyboard, so Escape reaches it, but a press
 * outside it is not swallowed. The press closes the panel, and every other one it missed, and
 * then does what it would have done without them: one click is enough to get from an open
 * menu to another, however deeply the first was nested.
 */
@OptIn(ExperimentalComposeUiApi::class)
private val Floating = PopupProperties(
    focusable = true,
    dismissOnBackPress = true,
    dismissOnClickOutside = true,
    clippingEnabled = true,
    usePlatformDefaultWidth = false,
    usePlatformInsets = true,
    consumePointerInputOutside = false,
)

/**
 * A menu that floats above everything else, attached to the element it is called inside. It
 * unfolds while [open] and folds back into the element once it is not, starting from and
 * ending at [unfoldFrom], the size of the element it slides out of, or a point. It asks to close
 * through [onDismiss] on Escape, a click anywhere outside of it, or the interface closing.
 *
 * One that is [attached] continues an element with a square edge, and is square along it too.
 *
 * ```
 * val menu = remember { MenuState() }
 * Box {
 *     Button("File", onClick = menu::toggle)
 *     Menu(menu.isOpen, onDismiss = menu::dismiss) {
 *         MenuItem("Save") { save() }
 *     }
 * }
 * ```
 */
@Composable
fun Menu(
    open: Boolean,
    onDismiss: () -> Unit,
    placement: MenuPlacement = MenuPlacement.Below,
    unfoldFrom: IntSize = IntSize.Zero,
    attached: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    MenuPopup(open, remember(placement) { UnfoldingPosition(placement::place) }, onDismiss, unfoldFrom, attached, content)
}

private fun MenuPlacement.place(anchor: IntRect, window: IntSize, popup: IntSize) = when (this) {
    MenuPlacement.Below -> IntOffset(
        anchor.left,
        if (anchor.bottom + popup.height <= window.height || anchor.top - popup.height < 0) anchor.bottom else anchor.top - popup.height
    )
    // Flips to the other side when the window ends before the popup does
    MenuPlacement.Beside -> IntOffset(
        if (anchor.right + popup.width <= window.width) anchor.right else anchor.left - popup.width,
        anchor.top
    )
}

/**
 * A panel that pops out beside the element it is called inside, the way a module's settings pop
 * out of its row: for an editor too big to sit in a row of its own. Like those it unfolds out
 * of the row while [open] and folds back into it once it is not. It asks to close through
 * [onDismiss] on Escape, a click anywhere outside of it, or the interface closing.
 */
@Composable
fun Popout(open: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val position = remember { UnfoldingPosition(MenuPlacement.Beside::place) }
    FloatingPanel(open, position, onDismiss, RoundedCornerShape(Radius.Panel)) { panel ->
        Column(panel.padding(3.dp), content = content)
    }
}

/** A [Menu] at [position], in scene pixels: the one a right click opens under the pointer */
@Composable
fun ContextMenu(open: Boolean, position: IntOffset, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) =
    MenuPopup(open, remember(position) { UnfoldingPosition { _, _, _ -> position } }, onDismiss, IntSize.Zero, false, content)

/**
 * [value], or while that is null, what it last was: what a popup goes on showing while it folds
 * away after the thing it showed has been let go.
 */
@Composable
fun <T : Any> rememberLast(value: T?): T? {
    val last = remember { LastValue<T>() }
    if (value != null) last.value = value
    return last.value
}

private class LastValue<T : Any> {
    var value: T? = null
}

/** Closes the whole chain of open menus, set by the outermost one */
private val LocalMenuDismiss = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Whether the menu around is open, so the submenus in it fold away along with it */
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
    // One that is attached is of a piece with a square edge of its element: square there too, on whichever side it opened
    val shape = when {
        !attached -> RoundedCornerShape(Radius.Menu)
        position.upwards -> RoundedCornerShape(topStart = Radius.Menu, topEnd = Radius.Menu)
        else -> RoundedCornerShape(bottomStart = Radius.Menu, bottomEnd = Radius.Menu)
    }
    FloatingPanel(open, position, onDismiss, shape, unfoldFrom) { panel ->
        CompositionLocalProvider(LocalMenuDismiss provides dismissAll, LocalMenuOpen provides open) {
            ScrollColumn(
                modifier = Modifier
                    // Bounded before the intrinsic width is asked for, which passes the height on
                    .heightIn(max = screenHeight)
                    .width(IntrinsicSize.Max)
                    // As wide as the element it drops out of, at least
                    .widthIn(min = maxOf(80.dp, with(LocalDensity.current) { unfoldFrom.width.toDp() }))
                    .then(panel)
                    // Entries run right up to the outline on every side, cut to its corners
                    .clip(shape),
                content = content
            )
        }
    }
}

/**
 * Every floating panel opens and folds away on the spring a module's settings panel does, quick
 * enough to keep up with fast clicking. Nothing waits for it: a press is acted on at once.
 */
val Unfold get() = Motion.panels

/**
 * The part of the interface some floating panels are open on behalf of: the settings panel
 * whose settings have menus, or a menu with a submenu beside it. While it is [closing] its
 * panels shut too, and are swallowed by its own folding away. One that lies over it is drawn
 * only inside what is left of it. One that hangs off an edge of it, as a submenu does, rides
 * that edge inwards and is cut off where the host ends along it. Nothing is left in the air.
 */
class PopupHost {
    var closing by mutableStateOf(false)

    /** What is on screen of it right now, in the window */
    var bounds by mutableStateOf(Rect.Zero)
        private set

    /** What was on screen of it when it started to close */
    var full = Rect.Zero
        private set

    fun place(bounds: Rect) {
        this.bounds = bounds
        if (!closing) full = bounds
    }
}

/** The [PopupHost] the floating panels opened from here belong to, if there is one */
val LocalPopupHost = compositionLocalOf<PopupHost?> { null }

/** Lets [host] swallow the element as it closes, as [PopupHost] describes */
private fun Modifier.swallowedBy(host: PopupHost?): Modifier = if (host == null) this else composed {
    var origin by remember { mutableStateOf(Offset.Zero) }
    onGloballyPositioned { origin = it.positionInWindow() }.drawWithContent {
        if (!host.closing) return@drawWithContent drawContent()
        val full = host.full
        val left = host.bounds
        val slack = Slack.toPx()
        // Off which edge of the host it hangs, if any, and how far that edge has come in
        val dx = when {
            origin.x >= full.right - slack -> left.right - full.right
            origin.x + size.width <= full.left + slack -> left.left - full.left
            else -> null
        }
        val dy = when {
            origin.y >= full.bottom - slack -> left.bottom - full.bottom
            origin.y + size.height <= full.top + slack -> left.top - full.top
            else -> null
        }
        // Cut to the host along an edge it does not hang off, far past its shadow along one it does
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

/** How far into its host a panel may reach and still count as hanging off its edge */
private val Slack = 4.dp

/**
 * What every floating panel shares. While [open] it unfolds out of the corner that touches the
 * element it belongs to, and once it is not it folds back into that corner before it is gone.
 * It takes input as [Floating] says, except while folding away: then it asks for nothing and
 * lets every key through.
 *
 * [content] is the panel, given the [Modifier] that makes it one.
 */
@Composable
private fun FloatingPanel(
    open: Boolean,
    position: UnfoldingPosition,
    onDismiss: () -> Unit,
    shape: Shape,
    unfoldFrom: IntSize = IntSize.Zero,
    content: @Composable (panel: Modifier) -> Unit,
) {
    // Gone at once with the interface, which leaves nothing on screen to fold away
    if (!LocalShown.current) {
        if (open) SideEffect(onDismiss)
        return
    }
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = open
    if (!open && visibility.isIdle && !visibility.currentState) return

    // Shut when what it belongs to is going, which swallows it, and host to what is opened from it in turn
    val host = LocalPopupHost.current
    if (host != null && host.closing && open) SideEffect(onDismiss)
    val own = remember { PopupHost() }
    SideEffect { own.closing = !open }

    val colors = LambdaTheme.colors
    Popup(
        popupPositionProvider = position,
        onDismissRequest = { if (open) onDismiss() },
        properties = Floating,
        onKeyEvent = { open && it.dismissOnEscape(onDismiss) },
    ) {
        if (open) KeyboardFocus.Layer()
        ContentColor(colors.onSurface) {
            // Unfolded by size alone, as a fade would draw it into a layer of its own with nothing behind the glass to blur
            AnimatedVisibility(
                visibleState = visibility,
                // The shadow is cast by what is unfolded, from outside the clip that does the unfolding
                modifier = Modifier
                    .swallowedBy(host)
                    .onGloballyPositioned { own.place(it.boundsInWindow()) }
                    .dropShadow(shape, close = true),
                enter = expandIn(Unfold, position.corner) { it.coerceAtMost(unfoldFrom) },
                exit = shrinkOut(Unfold, position.corner) { it.coerceAtMost(unfoldFrom) },
            ) {
                CompositionLocalProvider(LocalPopupHost provides own) {
                    content(Modifier.onSizeChanged { position.unfolded = it }.window(colors.panel, colors.border, shape))
                }
            }
        }
    }
}

/**
 * Places a popup that unfolds: where [position] puts it, kept on the screen, as big as it will
 * be once it is all there rather than as big as it is partway. That settles which side it
 * opens to before it starts to grow, and it grows away from the element that opened it.
 */
private class UnfoldingPosition(
    private val position: (anchor: IntRect, window: IntSize, popup: IntSize) -> IntOffset,
) : PopupPositionProvider {
    /** The size of the popup once unfolded, kept current by the popup */
    var unfolded = IntSize.Zero

    /** Whether it opened to the left of its element, and so grows leftwards */
    private var leftwards by mutableStateOf(false)

    /** Whether it opened above its element, and so grows upwards */
    var upwards by mutableStateOf(false)
        private set

    /** The corner it grows out of */
    val corner
        get() = when {
            upwards -> if (leftwards) Alignment.BottomEnd else Alignment.BottomStart
            else -> if (leftwards) Alignment.TopEnd else Alignment.TopStart
        }

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val full = IntSize(max(unfolded.width, popupContentSize.width), max(unfolded.height, popupContentSize.height))
        val wanted = position(anchorBounds, windowSize, full)
        leftwards = wanted.x + full.width <= anchorBounds.left
        upwards = wanted.y + full.height <= anchorBounds.top
        // Wherever it opens, all of it stays on the screen
        val x = wanted.x.coerceIn(0, (windowSize.width - full.width).coerceAtLeast(0))
        val y = wanted.y.coerceIn(0, (windowSize.height - full.height).coerceAtLeast(0))
        // Growing leftwards or upwards, it is the far edge that stays where it is
        return IntOffset(
            if (leftwards) x + full.width - popupContentSize.width else x,
            if (upwards) y + full.height - popupContentSize.height else y
        )
    }
}

/**
 * A list that drops down under the element it is called inside while that element keeps the
 * keyboard: what a search field shows its results in. Its right edge lines up with the
 * element's. A press outside of it calls [onDismiss].
 *
 * It unfolds downwards while [open] and folds back up once it is not, and in between it grows
 * and shrinks with what it lists rather than jumping from one length to the next.
 */
@Composable
fun Suggestions(open: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    // Gone at once with the interface, which leaves nothing on screen to fold away
    if (!LocalShown.current) {
        if (open) SideEffect(onDismiss)
        return
    }
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = open
    if (!open && visibility.isIdle && !visibility.currentState) return

    val colors = LambdaTheme.colors
    val shape = RoundedCornerShape(Radius.Menu)
    val screenHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    Popup(
        popupPositionProvider = remember {
            positionProvider { anchor, _, popup -> IntOffset((anchor.right - popup.width).coerceAtLeast(0), anchor.bottom) }
        },
        onDismissRequest = { if (open) onDismiss() },
        properties = PopupProperties(focusable = false),
    ) {
        ContentColor(colors.onSurface) {
            // By size alone and with the shadow cast from outside the clip, as every floating panel unfolds
            AnimatedVisibility(
                visibleState = visibility,
                modifier = Modifier.dropShadow(shape),
                enter = expandVertically(Unfold, Alignment.Top),
                exit = shrinkVertically(Unfold, Alignment.Top),
            ) {
                ScrollColumn(
                    modifier = Modifier
                        .heightIn(max = screenHeight * 0.6f)
                        .width(IntrinsicSize.Max)
                        .window(colors.panel, colors.border, shape)
                        .animateContentSize(Unfold)
                        .clip(shape),
                    content = content
                )
            }
        }
    }
}

/**
 * An entry of a [Menu]. Choosing it closes the menu, unless it is a [checked] item, which is
 * expected to be flipped back and forth. [selected] marks the one of several that is in effect.
 */
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
            // The chosen one is lifted and named in the second accent, so it stands out from the rest at a glance
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
        val color = when {
            !enabled -> colors.onSurfaceVariant
            selected -> colors.secondary
            else -> colors.onSurface
        }
        Text(label, Modifier.weight(1f), color, maxLines = 1)
        if (hint != null) Text(hint, Modifier.padding(start = 12.dp), colors.onSurfaceVariant, Type.label, maxLines = 1)
    }
}

/** A [MenuItem] that opens another menu beside it when clicked */
@Composable
fun Submenu(label: String, enabled: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val colors = LambdaTheme.colors
    val menu = remember { MenuState() }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (enabled) Modifier.hoverHighlight(colors.controlSelected).mouseClickable(onClick = menu::toggle) else Modifier)
                .padding(horizontal = 5.dp, vertical = 1.dp)
        ) {
            Text(label, Modifier.weight(1f), if (enabled) colors.onSurface else colors.onSurfaceVariant, maxLines = 1)
            Chevron(0f, Modifier.padding(start = 12.dp), colors.onSurfaceVariant)
        }
        Menu(menu.isOpen, onDismiss = menu::dismiss, MenuPlacement.Beside, content = content)
    }
}

@Composable
fun MenuLabel(label: String) =
    Text(label, Modifier.padding(horizontal = 5.dp, vertical = 1.dp), LambdaTheme.colors.onSurfaceVariant, maxLines = 1)

/**
 * A menu entry that only explains: resting the pointer on it shows [text], and clicking it does
 * nothing, so the menu stays open to be read from.
 */
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
            Text("?", Modifier.padding(start = 12.dp), colors.onSurfaceVariant, Type.label, maxLines = 1)
        }
    }
}

@Composable
fun MenuDivider() = Spacer(
    Modifier
        .fillMaxWidth()
        .padding(horizontal = 3.dp, vertical = 1.dp)
        .height(1.dp)
        .background(LambdaTheme.colors.border)
)

/**
 * A window in the middle of the screen that takes all input until it is closed. Clicking the
 * dimmed area around it, pressing Escape or closing the interface calls [onDismiss]; pass null
 * for a dialog that must be answered through its own buttons.
 */
@Composable
fun Dialog(onDismiss: (() -> Unit)?, content: @Composable BoxScope.() -> Unit) {
    // One that has to be answered waits for the interface to come back
    if (!LocalShown.current) {
        if (onDismiss != null) SideEffect(onDismiss)
        return
    }
    val colors = LambdaTheme.colors
    Popup(
        popupPositionProvider = remember { positionProvider { _, _, _ -> IntOffset.Zero } },
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
                        .window(colors.panel, colors.border, RoundedCornerShape(Radius.Panel), floating = true)
                        .padding(6.dp),
                    content = content
                )
            }
        }
    }
}

/**
 * Shows [text] under the pointer once it has rested on [content] for a moment, until it leaves
 * or presses. Blank text shows nothing.
 *
 * Compose's own `TooltipArea` takes any pointer movement as hovering. When the pointer moves
 * from the interface into a popup, the interface still hears of that move, at the pointer's new
 * position, and nothing after it: whatever lies under the popup there would put up its tooltip.
 * So only a real entry counts here.
 */
@Composable
fun Tooltip(text: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
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
            Popup(
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
                        .window(colors.panel, colors.border, RoundedCornerShape(Radius.Menu))
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
) = object : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ) = position(anchorBounds, windowSize, popupContentSize)
}

private fun IntSize.coerceAtMost(other: IntSize) = IntSize(minOf(width, other.width), minOf(height, other.height))
