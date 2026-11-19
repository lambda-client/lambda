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

package com.lambda.gui.clickgui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lambda.config.automation.AutomationConfig
import com.lambda.config.automation.UserAutomationConfig
import com.lambda.config.categories.UserAutomationCategory
import com.lambda.config.entries.Setting
import com.lambda.gui.component.Button
import com.lambda.gui.component.Dropdown
import com.lambda.gui.component.Expandable
import com.lambda.gui.component.LocalClickOnRelease
import com.lambda.gui.component.LocalPopupHost
import com.lambda.gui.component.PopupHost
import com.lambda.gui.component.RowHeight
import com.lambda.gui.component.ScrollColumn
import com.lambda.gui.component.Tooltip
import com.lambda.gui.component.Unfold
import com.lambda.gui.component.dropShadow
import com.lambda.gui.component.hoverHighlight
import com.lambda.gui.component.mouseClickable
import com.lambda.gui.component.mouseDraggable
import com.lambda.gui.component.window
import com.lambda.gui.core.LocalShown
import com.lambda.gui.core.ResizeVerticalIcon
import com.lambda.gui.core.awaitFrames
import com.lambda.gui.core.observe
import com.lambda.gui.menu.MenuBarHeight
import com.lambda.gui.settings.SettingRow
import com.lambda.gui.settings.SettingsList
import com.lambda.gui.settings.hasVisibleSettings
import com.lambda.gui.settings.holds
import com.lambda.gui.settings.pageTurn
import com.lambda.gui.settings.settingCell
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.Look
import com.lambda.gui.theme.Radius
import com.lambda.gui.theme.SoftTextShadow
import com.lambda.gui.theme.Text
import com.lambda.module.HudModule
import com.lambda.module.Module
import com.lambda.module.modules.client.AutoUpdater
import com.lambda.module.modules.client.Style
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** The panel never gets narrower than this, even when every setting has a short name */
private val MinWidth = 130.dp

/** Room between the edge of the panel and what it lists, the same on every side */
private val Padding get() = Look.panelPadding

/** A panel is given at least this much height, even under a module at the bottom of the screen */
private val MinHeight = 120.dp


/** The settings of [module] as a panel on screen, and the [row] of the module list it hangs off */
internal class SettingsPanelState(val module: Module) {
    /** Where the module's row is, in scene pixels. Kept current by the row itself. */
    var row by mutableStateOf(Rect.Zero)

    /** How much taller than the room under its row the user has made the panel, by dragging its top edge up */
    var extension by mutableStateOf(0.dp)

    /** Whether the list the row is in has finished unfolding. Kept current by the category window. */
    var settled by mutableStateOf(false)

    /** Which of its pages is turned to, kept while the panel is shut */
    var page by mutableStateOf(PanelPage.Settings)
}

/**
 * A module's settings, popped out of its category window beside the row that opened it.
 *
 * It opens to the right of the row and flips to the left when that would run off the screen.
 * It hangs from the top of the row and stays there: settings too long for the room underneath
 * scroll, so nothing moves under the pointer when a group is unfolded. While they scroll, the
 * top edge of the panel has a grip, and dragging that up makes the panel taller, above the row,
 * by as much as the user likes and the settings need, though never up under the menu bar. It unfolds while [open]
 * and folds away once it is not, calling [onClosed] when there is nothing left of it. Both
 * animations run on their own, so one panel can open while another is still closing.
 */
@Composable
internal fun SettingsPanel(panel: SettingsPanelState, open: Boolean, onClosed: () -> Unit, modifier: Modifier = Modifier) {
    val row = panel.row
    // The row reports where it is once it has been laid out with the panel attached
    if (row.isEmpty) return

    // Starts over, folded, each time the GUI is opened: a panel that was kept open unfolds
    // again, and only once the list its row is in has, so it does not ride the row down
    val visibility = remember(LocalShown.current) { MutableTransitionState(false) }
    val wanted = open && panel.settled
    // A menu left open on one of its settings folds away with the panel, inside what is left of it
    val popups = remember { PopupHost() }
    SideEffect { popups.closing = !wanted }
    visibility.targetState = wanted
    if (!open && visibility.isIdle && !visibility.currentState) {
        SideEffect(onClosed)
        return
    }

    val density = LocalDensity.current
    val screen = LocalWindowInfo.current.containerSize
    var size by remember { mutableStateOf(IntSize.Zero) }
    // How much of its height is unfolded right now
    var unfolded by remember { mutableStateOf(0) }

    // The room it may take: what is left under the top of the row, and what the user has
    // added by dragging the top edge up. A row at the very bottom still gets a panel tall enough to use.
    val scroll = rememberScrollState()
    val screenHeight = with(density) { screen.height.toDp() }
    val below = with(density) { (screen.height - row.top).toDp() }.coerceAtLeast(MinHeight)
    // Never up under the menu bar, where its top edge, and the grip on it, could not be reached again
    val highest = screenHeight - MenuBarHeight
    val maxHeight = (below + panel.extension).coerceAtMost(highest)

    // One dp of overlap lets the panel's border sit on the window's, so the seam is a single line
    val overlap = with(density) { 1.dp.toPx() }
    val flipped = row.right - overlap + size.width > screen.width && row.left + overlap - size.width >= 0
    val lift = (row.top + unfolded - screen.height)
        .coerceIn(0f, (row.top - with(density) { MenuBarHeight.toPx() }).coerceAtLeast(0f))

    // Square only on the corner that touches the row, which is no corner at all once lifted
    val shape = RoundedCornerShape(
        topStart = if (flipped || lift > 0f) Radius.Panel else 0.dp,
        topEnd = if (!flipped || lift > 0f) Radius.Panel else 0.dp,
        bottomEnd = Radius.Panel,
        bottomStart = Radius.Panel,
    )
    val seam = if (flipped) Alignment.TopEnd else Alignment.TopStart

    Box(
        modifier
            .offset {
                IntOffset(
                    (if (flipped) row.left + overlap else row.right - overlap).roundToInt(),
                    (row.top - lift).roundToInt()
                )
            }
            // A point on the seam: the panel grows out of it, to whichever side it opens
            .size(0.dp)
            .wrapContentSize(seam, unbounded = true)
    ) {
        // Unfolded by size alone. A fade would draw the panel into a layer of its own first,
        // where there is nothing behind the glass for it to blur.
        AnimatedVisibility(
            visibleState = visibility,
            // The shadow is cast by what is unfolded, from outside the clip that does the unfolding
            modifier = Modifier
                .onSizeChanged { unfolded = it.height }
                .onGloballyPositioned { popups.place(it.boundsInWindow()) }
                .dropShadow(RoundedCornerShape(Radius.Panel)),
            enter = expandIn(Unfold, seam),
            exit = shrinkOut(Unfold, seam),
        ) {
            val colors = LambdaTheme.colors
            // ...nor wider than the style allows. What is too long for it gives way rather than
            // widening the panel: a value slides along under the pointer.
            val maxWidth by observe { Style.settingsWidth.dp }
            Box(
                Modifier
                    .widthIn(MinWidth, maxWidth)
                    // Bounded before the intrinsic width is asked for: that query passes the
                    // height on, and the scrolling content cannot answer for an infinite one
                    .heightIn(max = maxHeight)
                    .widthOfContent()
                    .onSizeChanged { size = it }
                    .window(colors.panel, colors.border, shape)
            ) {
                CompositionLocalProvider(LocalPopupHost provides popups) {
                    Column(Modifier.padding(Padding)) { Pages(panel, scroll) }
                }
                // Only there while there is something to gain from it, or to take back
                if (scroll.maxValue > 0 || lift > 0f) {
                    Grip(
                        onDragStart = {
                            // Whatever was added but is not in use, the settings having got shorter since, is let go
                            panel.extension = panel.extension.coerceAtMost(with(density) { lift.toDp() })
                        },
                        onDrag = { up ->
                            val needed = panel.extension + with(density) { scroll.maxValue.toDp() }
                            panel.extension = (panel.extension + up).coerceIn(0.dp, needed.coerceAtMost(highest - below).coerceAtLeast(0.dp))
                        },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }
        }
    }
}

/**
 * A module's settings dropped down under its row, inside its window: what the style can have in
 * place of the panel beside it. They are as wide as the window, whatever they hold, and push the
 * modules under them down while [open]; a name or a value too long for the room slides along
 * under the pointer, as it does anywhere. They scroll with the list they are in, and come into
 * view when they open.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun InlineSettings(panel: SettingsPanelState, open: Boolean) {
    val intoView = remember { BringIntoViewRequester() }
    LaunchedEffect(open) {
        if (open) {
            awaitFrames(SETTLE_MILLIS)
            intoView.bringIntoView()
        }
    }
    Expandable(open) {
        val colors = LambdaTheme.colors
        Column(
            Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(intoView)
                // Filled as a panel beside the module would be and then shaded a little, a quarter
                // as far as what is pressed in: inside a window the fill alone is too close to it
                .background(colors.panel)
                .background(colors.inset.copy(alpha = colors.inset.alpha / 4))
                // And darker at its top and bottom edges, as what is set into a surface is where
                // the surface overhangs it
                .drawWithCache {
                    val depth = InsetShadow.toPx().coerceAtMost(size.height / 2)
                    val dark = colors.inset.copy(alpha = colors.inset.alpha / 2)
                    val top = Brush.verticalGradient(listOf(dark, Color.Transparent), 0f, depth)
                    val bottom = Brush.verticalGradient(listOf(Color.Transparent, dark), size.height - depth, size.height)
                    onDrawBehind {
                        drawRect(top, size = Size(size.width, depth))
                        drawRect(bottom, Offset(0f, size.height - depth), Size(size.width, depth))
                    }
                }
                .padding(Padding / 2)
        ) { Pages(panel, scroll = null) }
    }
}

/** How far the shade at the top and bottom of settings under their module reaches into them */
private val InsetShadow = 5.dp

/** How long settings take to drop down, before they are where they will stay */
private const val SETTLE_MILLIS = 200L

/**
 * What a module's settings are made of, in a panel or dropped down under its row: the strip
 * that turns their pages, and under it the page turned to. In a panel the page scrolls by
 * itself, on [scroll], under a strip that stays put; without one it is as long as it is.
 */
@Composable
private fun ColumnScope.Pages(panel: SettingsPanelState, scroll: ScrollState?) {
    val pages = remember(panel.module) { pagesOf(panel.module) }
    val page = panel.page.takeIf { it in pages } ?: PanelPage.Settings
    // A setting the search points at is on one of the pages: that one is turned to
    val revealed = Reveal.setting.takeIf { Reveal.module === panel.module }
    LaunchedEffect(revealed) {
        if (revealed != null) panel.page = pageOf(panel.module, revealed)
    }
    val scope = rememberCoroutineScope()

    SoftTextShadow {
        if (pages.size > 1) PageStrip(pages, page) {
            panel.page = it
            // A page turned to by hand starts at its top
            if (scroll != null) scope.launch { scroll.scrollTo(0) }
        }
        // Turned like the tabs a module's settings bring
        val turned = @Composable {
            AnimatedContent(
                targetState = page,
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = { pageTurn(towardsEnd = targetState > initialState) },
            ) { shown ->
                Column(Modifier.fillMaxWidth()) {
                    when (shown) {
                        PanelPage.Settings -> SettingsPage(panel.module)
                        PanelPage.Module -> ModulePage(panel.module)
                        PanelPage.Automation -> AutomationPage(panel.module)
                    }
                }
            }
        }
        if (scroll != null) ScrollColumn(Modifier.weight(1f, fill = false), scroll) { turned() } else turned()
    }
}

/**
 * Makes the element as wide as what it holds would like to be, and when that changes, a group
 * having been unfolded or folded away, takes it there over a moment rather than at once.
 */
private fun Modifier.widthOfContent(): Modifier = composed {
    val scope = rememberCoroutineScope()
    // Created at the width first asked for, so a panel opens at its size instead of growing into it
    var width by remember { mutableStateOf<Animatable<Float, AnimationVector1D>?>(null) }
    layout { measurable, constraints ->
        val wanted = measurable.maxIntrinsicWidth(constraints.maxHeight)
            .coerceIn(constraints.minWidth, constraints.maxWidth).toFloat()
        val current = width ?: Animatable(wanted).also { width = it }
        if (current.targetValue != wanted) scope.launch { current.animateTo(wanted, WidthChange) }

        val placeable = measurable.measure(Constraints.fixedWidth(current.value.roundToInt()).copy(maxHeight = constraints.maxHeight))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

private val WidthChange = spring<Float>(stiffness = Spring.StiffnessMedium)

/**
 * The grip along the top edge of a panel whose settings scroll. Dragging it [up] by some
 * distance, or down by a negative one, is how the panel is made taller and shorter.
 */
@Composable
private fun Grip(onDragStart: () -> Unit, onDrag: (up: Dp) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val color = LambdaTheme.colors.onSurfaceVariant
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(Padding)
            .pointerHoverIcon(ResizeVerticalIcon)
            .mouseDraggable(onDragStart = onDragStart) { delta -> onDrag(with(density) { -delta.y.toDp() }) }
    ) {
        Spacer(Modifier.size(16.dp, 1.dp).background(color, RoundedCornerShape(50)))
    }
}

/**
 * What a settings panel can show. A module's own settings are the first page, and what every
 * module has is kept off it on pages of its own, so the first is never longer than the module
 * makes it.
 */
internal enum class PanelPage(val title: String) {
    Settings("Settings"),
    Module("Module"),
    Automation("Automation"),
}

/** The pages [module] has: all of them, short of the automation of a module that has none */
private fun pagesOf(module: Module): List<PanelPage> = when {
    module == AutoUpdater -> listOf(PanelPage.Settings)
    module.automationConfig === AutomationConfig.DEFAULT -> listOf(PanelPage.Settings, PanelPage.Module)
    else -> PanelPage.entries
}

/** The page of [module]'s panel that [setting] is on */
private fun pageOf(module: Module, setting: Setting<*>): PanelPage = when {
    module.automationConfig !== AutomationConfig.DEFAULT && module.automationConfig.settingLayers.holds(setting) -> PanelPage.Automation
    setting in moduleSettings(module) -> PanelPage.Module
    else -> PanelPage.Settings
}

private fun moduleSettings(module: Module): List<Setting<*>> = listOfNotNull(
    // What only matters to a module that can be toggled is left out for one that cannot
    module.keybindSetting.takeUnless { module.alwaysEnabled },
    module.prioritySetting,
    module.disableOnReleaseSetting.takeUnless { module.alwaysEnabled },
    module.drawSetting.takeUnless { module.alwaysEnabled },
    (module as? HudModule)?.backgroundColor,
)

/**
 * The strip along the top of a panel that turns its pages. It spans the panel, each page with
 * a share to match its name, and marks the chosen page with a line under its name, where the tabs a module's settings
 * bring are filled: the two are told apart at a glance when one sits over the other.
 */
@Composable
private fun PageStrip(pages: List<PanelPage>, selected: PanelPage, onSelect: (PanelPage) -> Unit) {
    val colors = LambdaTheme.colors
    Row(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
        pages.forEach { page ->
            val isSelected = page == selected
            val line by animateColorAsState(if (isSelected) colors.secondary else colors.raised)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    // By the length of its name and its padding: in equal parts a long name
                    // would be cut short in a narrow panel while a short one had room to spare
                    .weight(page.title.length + 2f)
                    .height(RowHeight + 1.dp)
                    .hoverHighlight(colors.raised)
                    .mouseClickable { onSelect(page) }
                    .drawBehind {
                        val thickness = 1.dp.toPx()
                        drawRect(line, Offset(0f, size.height - thickness), Size(size.width, thickness))
                    }
                    .padding(horizontal = 4.dp)
            ) {
                Text(page.title, color = if (isSelected) colors.onSurface else colors.onSurfaceVariant, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** The module's own settings */
@Composable
private fun SettingsPage(module: Module) {
    val any by observe { module.settingLayers.hasVisibleSettings() }
    if (any) SettingsList(module.settingLayers)
    else Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Text("No settings", color = LambdaTheme.colors.onSurfaceVariant)
    }
}

/** Which automation config the module follows, and that config's settings */
@Composable
private fun AutomationPage(module: Module) {
    val linked by observe { module.backingAutomationConfig }
    // Among the settings and changed like one: when the button is let go
    CompositionLocalProvider(LocalClickOnRelease provides true) { AutomationConfigSelector(module, linked) }
    // The linked config's values are aliased into the module's own, so the list is
    // rebuilt when the link changes to pick up the swapped ones
    key(linked) { SettingsList(module.automationConfig.settingLayers) }
}

/** The settings every module has */
@Composable
private fun ModulePage(module: Module) {
    moduleSettings(module).forEach { SettingRow(it) }
    Tooltip("Resets all settings for this module to their default values", Modifier.fillMaxWidth()) {
        CompositionLocalProvider(LocalClickOnRelease provides true) {
            Button("Reset", Modifier.settingCell().height(RowHeight)) { module.resetSettings() }
        }
    }
}

/** Picks which automation config the module follows: its own, or one of the user's */
@Composable
private fun AutomationConfigSelector(module: Module, linked: AutomationConfig) {
    val options by observe {
        listOf(module.defaultAutomationConfig) + UserAutomationCategory.configs.filterIsInstance<AutomationConfig>()
    }
    Dropdown(
        label = "Linked Config",
        options = options,
        selected = linked,
        optionLabel = { it.name },
        modifier = Modifier.settingCell().padding(start = 3.dp),
    ) { selected ->
        (module.backingAutomationConfig as? UserAutomationConfig)?.linkedModules?.value?.remove(module.name)
        (selected as? UserAutomationConfig)?.linkedModules?.value?.add(module.name)
        module.automationConfig = selected
    }
}
