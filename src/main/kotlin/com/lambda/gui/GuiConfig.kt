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

package com.lambda.gui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lambda.config.Config
import com.lambda.config.ConfigBlock
import com.lambda.config.Group
import com.lambda.config.Tab
import com.lambda.config.categories.GuiCategory
import com.lambda.config.entries.Setting
import com.lambda.config.entries.onHold
import com.lambda.config.entries.onValueChange
import com.lambda.config.entries.onValueChangeUnsafe
import com.lambda.config.settings.appliedOnRelease
import com.lambda.config.settings.collections.collectionSetting
import com.lambda.config.settings.comparable.EnumSetting
import com.lambda.config.settings.comparable.boolSetting
import com.lambda.config.settings.comparable.enumSetting
import com.lambda.config.settings.comparable.onSelectionHover
import com.lambda.config.settings.complex.colorSetting
import com.lambda.config.settings.complex.setting
import com.lambda.config.settings.functionSetting
import com.lambda.config.settings.numeric.floatSetting
import com.lambda.config.settings.numeric.intSetting
import com.lambda.core.Loadable
import com.lambda.event.events.ComposeEvent
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.gui.theme.GuiFont
import com.lambda.gui.theme.GuiInterrupt
import com.lambda.gui.theme.GuiTransition
import com.lambda.gui.theme.NotificationFlow
import com.lambda.gui.theme.NotificationLimit
import com.lambda.gui.theme.NotificationSide
import com.lambda.gui.theme.Palette
import com.lambda.util.KeyCode
import com.lambda.util.NamedEnum
import com.lambda.util.WindowUtils.setLambdaWindowIcon
import com.lambda.util.math.toCompose
import net.minecraft.SharedConstants
import net.minecraft.client.util.Icons
import org.jetbrains.skiko.SystemTheme
import org.jetbrains.skiko.currentSystemTheme
import java.awt.Color

@Suppress("unused")
object GuiConfig : Loadable, Config("GUI", GuiCategory) {
    private const val GENERAL_TAB = "General"
    private const val SNAPPING_TAB = "Snapping"
    private const val STYLE_TAB = "Style"
    private const val BACKDROP_GROUP = "Backdrop"
    private const val SHAPE_GROUP = "Shape"
    private const val LAYOUT_GROUP = "Layout"
    private const val MOTION_GROUP = "Motion"
    private const val NOTIFICATIONS_GROUP = "Notifications"
    private const val NOTIFICATION_PREVIEW_MILLIS = 1500L
    private const val DARK_THEME_GROUP = "Dark Theme"
    private const val LIGHT_THEME_GROUP = "Light Theme"

    @Tab(GENERAL_TAB) val keybind by setting("Keybind", KeyCode.Y, "Opens and closes the click GUI", screenCheck = false)
        .onPressUnsafe { LambdaGui.toggle() }
    @Tab(GENERAL_TAB) val setLambdaWindowIcon by boolSetting("Set Lambda Window Icon", true)
        .onValueChange { _, to ->
            if (to) setLambdaWindowIcon()
            else {
                val icon =
                    if (SharedConstants.getGameVersion().stable()) Icons.RELEASE
                    else Icons.SNAPSHOT
                mc.window.setIcon(mc.defaultResourcePack, icon)
            }
        }
    @JvmStatic
    @Tab(GENERAL_TAB) val setLambdaWindowTitle by boolSetting("Set Lambda Window Title", true)
        .onValueChange { _, _ -> mc.updateWindowTitle() }
    @Tab(GENERAL_TAB) val lambdaTitleAppendixName by boolSetting("Append Username", true) { setLambdaWindowTitle }
        .onValueChange { _, _ -> mc.updateWindowTitle() }
    @Tab(GENERAL_TAB) val keepSettingsOpen by boolSetting("Keep Settings Open", false, "Leaves a module's settings open when the click GUI closes, so they are still there when it opens again")
    @Tab(GENERAL_TAB) val hoverSubmenus by boolSetting("Open Submenus On Hover", true, "Opens a submenu when the pointer is over its row. When off, a submenu opens on a click.")
    @Tab(GENERAL_TAB) private val resetWindowLayout by functionSetting("Reset Window Layout", ::resetWindows, "Puts every category window of the click GUI back in its place in the row, unfolded")

    @Tab(SNAPPING_TAB) val snapEnabled by boolSetting("Enable Snapping", true, "Master toggle for snapping HUD elements and click GUI windows into line when they are dragged")
    @Tab(SNAPPING_TAB) val snapKey by setting("Snap Key", KeyCode.LeftShift, "What is dragged only snaps while this is held. Unbind it to snap all the time.") { snapEnabled }
    @Tab(SNAPPING_TAB) val showSnapLines by boolSetting("Show Snap Lines", false, "Draw the grid, and the line an element snaps to, while it is dragged") { snapEnabled }
    @Tab(SNAPPING_TAB) val gridSize by floatSetting("Grid Size", 5f, 2f..128f, 1f, "Grid step") { snapEnabled }
    @Tab(SNAPPING_TAB) val snapToEdges by boolSetting("Snap To Element Edges", true) { snapEnabled }
    @Tab(SNAPPING_TAB) val snapToCenters by boolSetting("Snap To Element Centers", true) { snapEnabled }
    @Tab(SNAPPING_TAB) val snapToScreenCenter by boolSetting("Snap To Screen Center", true) { snapEnabled }
    @Tab(SNAPPING_TAB) val snapToGrid by boolSetting("Snap To Grid", true) { snapEnabled }
    @Tab(SNAPPING_TAB) val snapDistanceElement by floatSetting("Snap Distance (Elements)", 20f, 1f..48f, 1f) { snapEnabled }
    @Tab(SNAPPING_TAB) val snapDistanceScreen by floatSetting("Snap Distance (Screen Center)", 14f, 1f..48f, 1f) { snapEnabled }
    @Tab(SNAPPING_TAB) val snapDistanceGrid by floatSetting("Snap Distance (Grid)", 12f, 1f..48f, 1f) { snapEnabled }
    @Tab(SNAPPING_TAB) val snapLineColor by colorSetting("Snap Line Color", Color(255, 160, 0, 220)) { snapEnabled && showSnapLines }

    private enum class ThemeMode(override val displayName: String) : NamedEnum {
        Auto("Auto"),
        Dark("Dark"),
        Light("Light"),
    }

    @Tab(STYLE_TAB) private val themeMode by enumSetting("Theme", ThemeMode.Dark, "Auto follows the theme of your operating system")
    @Tab(STYLE_TAB) val font by enumSetting("Font", GuiFont.Minecraft, "The Minecraft font is sharpest where the interface is a whole number of times its natural size: at 1080p and 2160p with a scale of 1")
    @Tab(STYLE_TAB) val fontSize by floatSetting("Font Size", 0.85f, 0.5f..1.2f, 0.05f, "Size of text, as a share of the size the font reads best at. Rows grow to fit larger text. The Minecraft font is only sharp at 1")
    @Tab(STYLE_TAB) val scale by floatSetting("Scale", 1f, 0.5f..1.5f, 0.05f, "Size of the interface. Dragged, it is applied when the button is let go. No larger than leaves room on the screen for every window and dialog").appliedOnRelease()

    @Tab(STYLE_TAB) @Group(BACKDROP_GROUP) val dimBackground by boolSetting("Dim Background", true, "Darkens what is behind the click GUI while it is open, whatever NoRender says about GUI shadows")
    @Tab(STYLE_TAB) @Group(BACKDROP_GROUP) val dimStrength by floatSetting("Dim Strength", 0.6f, 0.05f..1f, 0.05f, "How dark the background gets. Minecraft's own menus use about 0.8") { dimBackground }
    @Tab(STYLE_TAB) @Group(BACKDROP_GROUP) val blur by boolSetting("Blur", true, "Blurs what is behind windows for a frosted glass look")
    @Tab(STYLE_TAB) @Group(BACKDROP_GROUP) val blurRadius by floatSetting("Blur Radius", 4f, 1f..32f, 1f, "Strength of the blur") { blur }
    @Tab(STYLE_TAB) @Group(BACKDROP_GROUP) val layerFade by floatSetting("Layer Fade", 0.5f, 0f..1f, 0.05f, "How far the interface fades out underneath a panel, menu or window opened over it, letting the game show there instead so the two do not run together")
    @Tab(STYLE_TAB) @Group(BACKDROP_GROUP) val glow by boolSetting("Glow", false, "Surrounds the click GUI with a glow, in the two glow colours of the theme in use")
    @Tab(STYLE_TAB) @Group(BACKDROP_GROUP) val glowRadius by floatSetting("Glow Radius", 17f, 1f..100f, 1f) { glow }
    @Tab(STYLE_TAB) @Group(BACKDROP_GROUP) val glowIntensity by floatSetting("Glow Intensity", 0.3f, 0.1f..3f, 0.1f) { glow }

    @Tab(STYLE_TAB) @Group(SHAPE_GROUP) val windowRounding by floatSetting("Window Rounding", 3f, 0f..12f, 0.5f, "Corners of the category windows, and of the band of tabs that is made like their headers. Square at 0")
    @Tab(STYLE_TAB) @Group(SHAPE_GROUP) val panelRounding by floatSetting("Panel Rounding", 5f, 0f..16f, 0.5f, "Corners of a module's settings panel, of dialogs and of the colour picker")
    @Tab(STYLE_TAB) @Group(SHAPE_GROUP) val menuRounding by floatSetting("Menu Rounding", 3f, 0f..12f, 0.5f, "Corners of menus, dropdowns, search results and tooltips")
    @Tab(STYLE_TAB) @Group(SHAPE_GROUP) val controlRounding by floatSetting("Control Rounding", 2f, 0f..8f, 0.5f, "Corners of sliders, chips, buttons, fields and group headings. Tick boxes get half of it")
    @Tab(STYLE_TAB) @Group(SHAPE_GROUP) val outlines by boolSetting("Outlines", true, "Draws a thin border around windows, panels and menus. Off for the look of pure glass")
    @Tab(STYLE_TAB) @Group(SHAPE_GROUP) val outlineWidth by floatSetting("Outline Width", 0.5f, 0.5f..3f, 0.5f, "Thickness of the outlines. 0.5 is one pixel at 1080p") { outlines }
    @Tab(STYLE_TAB) @Group(SHAPE_GROUP) val shadows by boolSetting("Shadows", true, "Casts a soft shadow from panels, menus and dialogs onto the interface beneath them")
    @Tab(STYLE_TAB) @Group(SHAPE_GROUP) val emboss by boolSetting("Emboss", false, "Lights each module faintly from above, so the list feels less flat")

    @Tab(STYLE_TAB) @Group(LAYOUT_GROUP) val elementHeight by floatSetting("Element Height", 11.5f, 10f..24f, 0.5f, "Height of a module in its category and of a row of settings, never less than its text needs")
    @Tab(STYLE_TAB) @Group(LAYOUT_GROUP) val windowWidth by floatSetting("Window Width", 90f, 60f..160f, 5f, "Width of the category windows. Names too long for it slide under the pointer")
    @Tab(STYLE_TAB) @Group(LAYOUT_GROUP) val settingsWidth by floatSetting("Settings Width", 180f, 130f..320f, 10f, "The widest a module's settings may get. Values too long for it slide under the pointer")
    @Tab(STYLE_TAB) @Group(LAYOUT_GROUP) val windowGap by floatSetting("Window Gap", 2f, 0f..20f, 1f, "Room between the category windows in their row, and what one snaps to beside another")
    @Tab(STYLE_TAB) @Group(LAYOUT_GROUP) val windowTop by floatSetting("Window Top", 94f, 20f..300f, 2f, "How far down the screen the row of category windows starts, for those that have not been moved")
    @Tab(STYLE_TAB) @Group(LAYOUT_GROUP) val modulePadding by floatSetting("Module Padding", 4f, 0f..12f, 0.5f, "Room to either side of a module's name, and of its category's")
    @Tab(STYLE_TAB) @Group(LAYOUT_GROUP) val panelPadding by floatSetting("Panel Padding", 4f, 0f..12f, 0.5f, "Room around the settings in a module's settings panel")
    @Tab(STYLE_TAB) @Group(LAYOUT_GROUP) val settingSpacing by floatSetting("Setting Spacing", 1f, 0f..6f, 0.5f, "Room between one setting and the next")
    @Tab(STYLE_TAB) @Group(LAYOUT_GROUP) val groupIndent by floatSetting("Group Indent", 5f, 0f..12f, 0.5f, "How far the settings of a group are set in behind its bar")
    @Tab(STYLE_TAB) @Group(LAYOUT_GROUP) val inlineSettings by boolSetting("Inline Settings", false, "A module's settings drop down under it inside its window, as wide as the window and pushing the modules below it down, instead of popping out beside it")
    @Tab(STYLE_TAB) @Group(LAYOUT_GROUP) val multipleSettings by boolSetting("Multiple Settings", false, "Lets the settings of several modules be open at once, where otherwise opening one closes the last. A click beside them closes them all either way")

    @Tab(STYLE_TAB) @Group(MOTION_GROUP) val animations by boolSetting("Animations", true, "Unfolds, slides and fades the interface. Off, everything but scrolling is where it is going at once")
    @Tab(STYLE_TAB) @Group(MOTION_GROUP) val animationSpeed by floatSetting("Animation Speed", 1f, 0.25f..4f, 0.25f, "How fast the interface animates, as a multiple of what it is made with") { animations }
    @Tab(STYLE_TAB) @Group(MOTION_GROUP) val openAnimation by boolSetting("Open Animation", true, "Animates the click GUI in when it opens") { animations }
    @Tab(STYLE_TAB) @Group(MOTION_GROUP) val openStyle by collectionSetting("Open Style", setOf(GuiTransition.Unfold), GuiTransition.entries.toSet(), "What the click GUI does as it comes in, any of them together: its windows unfold out of their headers, and all of it fades, slides down or zooms in") { animations && openAnimation }
    @Tab(STYLE_TAB) @Group(MOTION_GROUP) val closeAnimation by boolSetting("Close Animation", true, "Animates the click GUI out when it closes. The game has the mouse and keyboard back at once: the animation only plays over it") { animations }
    @Tab(STYLE_TAB) @Group(MOTION_GROUP) val closeStyle by collectionSetting("Close Style", setOf(GuiTransition.Unfold, GuiTransition.Fade), GuiTransition.entries.toSet(), "What the click GUI does as it goes out, any of them together: its windows fold into their headers, and all of it fades, slides up or zooms out") { animations && closeAnimation }
    @Tab(STYLE_TAB) @Group(MOTION_GROUP) val interruptedAnimation by enumSetting("Interrupted Animation", GuiInterrupt.Reverse, "What happens when the click GUI is opened or closed again before its animation is done: it turns around from where it is, or skips to the end and plays the other one in full") { animations && (openAnimation || closeAnimation) }
    @Tab(STYLE_TAB) @Group(MOTION_GROUP) val windowAnimations by boolSetting("Window Animations", true, "Category windows unfold out of their headers") { animations }
    @Tab(STYLE_TAB) @Group(MOTION_GROUP) val panelAnimations by boolSetting("Panel Animations", true, "Settings panels, menus, dropdowns and search results unfold out of what opened them") { animations }
    @Tab(STYLE_TAB) @Group(MOTION_GROUP) val pageAnimations by boolSetting("Page Animations", true, "Changing the tab or the page of a settings panel slides the new one in") { animations }
    @Tab(STYLE_TAB) @Group(MOTION_GROUP) val dialogAnimations by boolSetting("Dialog Animations", true, "Dialogs such as GUI Settings grow out of the centre of the screen as they open, and shrink back as they close") { animations }
    @Tab(STYLE_TAB) @Group(MOTION_GROUP) val smoothScrolling by boolSetting("Smooth Scrolling", true, "The mouse wheel sets a list off fast and lets it glide to rest. On its own: it works with Animations off, and at its own pace")
    @Tab(STYLE_TAB) @Group(MOTION_GROUP) val scrollSpeed by floatSetting("Scroll Speed", 1f, 0.25f..3f, 0.25f, "How far a notch of the mouse wheel takes a list, as a multiple of three rows")
    @Tab(STYLE_TAB) @Group(MOTION_GROUP) val tooltipDelay by intSetting("Tooltip Delay", 500, 0..2000, 50, "How long the pointer rests on something before its description shows", " ms")

    @Tab(STYLE_TAB) @Group(NOTIFICATIONS_GROUP) val notificationScale by floatSetting("Notification Scale", 1f, 0.5f..3f, 0.05f, "Size of notifications, on top of the scale of the rest of the interface").previewsNotifications()
    @Tab(STYLE_TAB) @Group(NOTIFICATIONS_GROUP) val notificationWidth by floatSetting("Notification Width", 130f, 80f..300f, 10f, "How wide a notification is. Longer messages wrap onto more lines").previewsNotifications()
    @Tab(STYLE_TAB) @Group(NOTIFICATIONS_GROUP) val notificationSide by enumSetting("Notification Side", NotificationSide.Right, "Which side of the screen notifications slide in from").previewsNotificationOptions()
    @Tab(STYLE_TAB) @Group(NOTIFICATIONS_GROUP) val notificationHeight by floatSetting("Notification Height", 0f, 0f..1f, 0.05f, "Where on that side the first notification sits. 0 is the bottom of the screen and 1 the top").previewsNotifications()
    @Tab(STYLE_TAB) @Group(NOTIFICATIONS_GROUP) val notificationFlow by enumSetting("Notification Direction", NotificationFlow.Up, "Which way the stack grows: each new notification appears on this side of the ones already showing. They leave the other way, and the stack is kept on the screen").previewsNotificationOptions()
    @Tab(STYLE_TAB) @Group(NOTIFICATIONS_GROUP) val notificationSeconds by floatSetting("Notification Time", 4f, 1f..15f, 0.5f, "How many seconds a notification stays before it leaves")
    @Tab(STYLE_TAB) @Group(NOTIFICATIONS_GROUP) val notificationLimit by enumSetting("Notification Limit", NotificationLimit.Count, "Count lets the oldest go once more than Max Notifications are showing. Fit Screen shows as many as fit between Notification Height and the edge of the screen the stack grows towards, and the oldest goes when a new one would not fit").previewsNotificationOptions()
    @Tab(STYLE_TAB) @Group(NOTIFICATIONS_GROUP) val maxNotifications by intSetting("Max Notifications", 5, 1..12, 1, "How many notifications show at once before the oldest leaves") { notificationLimit == NotificationLimit.Count }.previewsNotifications()

    @Tab(STYLE_TAB) @Group(DARK_THEME_GROUP) private val darkTheme by configBlock(
        PaletteSettings(
            this,
            primary = Color(214, 77, 113),
            secondary = Color(255, 141, 128),
            surface = Color(69, 48, 55, 165),
            panel = Color(55, 38, 45, 200),
            inset = Color(0, 0, 0, 190),
            onSurface = Color(246, 236, 239),
            onSurfaceVariant = Color(184, 156, 166),
            header = Color(222, 53, 101),
            onHeader = Color(255, 246, 248),
            moduleEnabled = Color(161, 42, 75),
            onModuleEnabled = Color(255, 240, 244),
            control = Color(214, 77, 113),
            controlSelected = Color(92, 64, 75),
            onControl = Color(246, 236, 239),
            track = Color(38, 28, 32, 230),
            input = Color(32, 23, 26, 230),
            border = Color(128, 92, 104, 235),
            textShadow = Color(0, 0, 0, 150),
            glowA = Color(222, 53, 101),
            glowB = Color(255, 141, 128),
        )
    )

    @Tab(STYLE_TAB) @Group(LIGHT_THEME_GROUP) private val lightTheme by configBlock(
        PaletteSettings(
            this,
            primary = Color(222, 53, 101),
            secondary = Color(209, 74, 59),
            surface = Color(250, 236, 241, 228),
            panel = Color(244, 226, 232, 240),
            inset = Color(120, 0, 40, 68),
            onSurface = Color(61, 40, 47),
            onSurfaceVariant = Color(110, 86, 95),
            header = Color(222, 53, 101),
            onHeader = Color(255, 250, 251),
            moduleEnabled = Color(232, 84, 126),
            onModuleEnabled = Color(255, 250, 251),
            control = Color(240, 139, 166),
            controlSelected = Color(224, 197, 206),
            onControl = Color(61, 40, 47),
            track = Color(209, 192, 199, 220),
            input = Color(255, 250, 252, 210),
            border = Color(184, 154, 164, 238),
            textShadow = Color(0, 0, 0, 0),
            glowA = Color(222, 53, 101),
            glowB = Color(255, 141, 128),
        )
    )

    private val theme: PaletteSettings
        get() =
            when (themeMode) {
                ThemeMode.Dark -> darkTheme
                ThemeMode.Light -> lightTheme
                ThemeMode.Auto ->
                    if (SystemAppearance.isDark) darkTheme
                    else lightTheme
            }

    val palette: Palette get() = theme.toPalette()
    val glowColorA: Color get() = theme.glowA
    val glowColorB: Color get() = theme.glowB

    private object SystemAppearance {
        private const val REFRESH_MILLIS = 1000L

        private var checkedAt = 0L
        private var dark = true

        val isDark: Boolean
            get() {
                val now = System.currentTimeMillis()
                if (now - checkedAt > REFRESH_MILLIS) {
                    checkedAt = now
                    dark = currentSystemTheme != SystemTheme.LIGHT
                }
                return dark
            }
    }

    private var notificationHeld = false
    private var notificationPreviewEnd = 0L
    internal var notificationOption by mutableStateOf<Any?>(null)
        private set

    init {
        listenUnsafe<ComposeEvent.Gui.Post> { event ->
            val previewing = notificationHeld || notificationOption != null || System.currentTimeMillis() < notificationPreviewEnd
            if (previewing && !LambdaGui.leaving) event.content { NotificationPreview() }
        }
    }

    private fun <S : Setting<T>, T> S.previewsNotifications() =
        apply {
            onHold { notificationHeld = it }
            onValueChangeUnsafe { _, _ -> notificationPreviewEnd = System.currentTimeMillis() + NOTIFICATION_PREVIEW_MILLIS }
        }

    private fun <S : EnumSetting<T>, T : Enum<T>> S.previewsNotificationOptions() =
        onSelectionHover { notificationOption = it }

    internal data class WindowPlacement(
        val x: Float,
        val y: Float,
        val collapsed: Boolean
    ) {
        val inRow get() = x.isNaN() || y.isNaN()

        companion object {
            fun inRow(collapsed: Boolean) = WindowPlacement(Float.NaN, Float.NaN, collapsed)
        }
    }

    private var windowPlacements by property("")
    private var decodedFrom: String? = null
    private var decoded = emptyMap<String, WindowPlacement>()

    internal var windows: Map<String, WindowPlacement>
        get() {
            val saved = windowPlacements
            if (saved != decodedFrom) {
                decodedFrom = saved
                decoded = saved.split(';').mapNotNull(::decodeWindow).toMap()
            }
            return decoded
        }
        set(value) {
            windowPlacements =
                value.entries.joinToString(";") { (tag, window) ->
                    "$tag:${window.x},${window.y},${window.collapsed}"
                }
        }

    internal fun resetWindows() {
        windows = emptyMap()
    }

    private fun decodeWindow(entry: String): Pair<String, WindowPlacement>? {
        val (tag, fields) = entry.split(':').takeIf { it.size == 2 } ?: return null
        val (x, y, collapsed) = fields.split(',').takeIf { it.size == 3 } ?: return null
        return tag to WindowPlacement(
            x.toFloatOrNull() ?: return null,
            y.toFloatOrNull() ?: return null,
            collapsed.toBoolean()
        )
    }
}

class PaletteSettings(
    override val c: Config,
    primary: Color,
    secondary: Color,
    surface: Color,
    panel: Color,
    inset: Color,
    onSurface: Color,
    onSurfaceVariant: Color,
    header: Color,
    onHeader: Color,
    moduleEnabled: Color,
    onModuleEnabled: Color,
    control: Color,
    controlSelected: Color,
    onControl: Color,
    track: Color,
    input: Color,
    border: Color,
    textShadow: Color,
    glowA: Color,
    glowB: Color,
) : ConfigBlock {
    private val primary by c.colorSetting("Primary", primary, "Accent colour: the fill of sliders and ticked boxes")
    private val secondary by c.colorSetting("Secondary", secondary, "Second accent: what is chosen among options, as in a menu or a row of pages")
    private val surface by c.colorSetting("Surface", surface, "Fill of windows. Lower its opacity to see more of the blur")
    private val panel by c.colorSetting("Panel", panel, "Fill of settings panels, menus and dialogs")
    private val inset by c.colorSetting("Inset", inset, "Shade behind settings that open under their module, and over the module whose settings are open beside it")
    private val onSurface by c.colorSetting("On Surface", onSurface, "Text on windows")
    private val onSurfaceVariant by c.colorSetting("On Surface Variant", onSurfaceVariant, "Subdued text on windows")
    private val header by c.colorSetting("Header", header)
    private val onHeader by c.colorSetting("On Header", onHeader)
    private val moduleEnabled by c.colorSetting("Module Enabled", moduleEnabled)
    private val onModuleEnabled by c.colorSetting("On Module Enabled", onModuleEnabled)
    private val control by c.colorSetting("Control", control, "Fill of dropdowns, buttons and key caps")
    private val controlSelected by c.colorSetting("Control Selected", controlSelected, "Fill of the entry of a menu or list that is chosen or under the pointer")
    private val onControl by c.colorSetting("On Control", onControl)
    private val track by c.colorSetting("Slider Track", track)
    private val input by c.colorSetting("Text Field", input)
    private val border by c.colorSetting("Border", border)
    private val textShadow by c.colorSetting("Text Shadow", textShadow)
    val glowA by c.colorSetting("Glow Color 1", glowA, "Where the glow around the click GUI starts") { GuiConfig.glow }
    val glowB by c.colorSetting("Glow Color 2", glowB, "Where the glow around the click GUI ends") { GuiConfig.glow }

    fun toPalette() =
        Palette(
            primary = primary.toCompose(),
            secondary = secondary.toCompose(),
            surface = surface.toCompose(),
            panel = panel.toCompose(),
            inset = inset.toCompose(),
            onSurface = onSurface.toCompose(),
            onSurfaceVariant = onSurfaceVariant.toCompose(),
            header = header.toCompose(),
            onHeader = onHeader.toCompose(),
            moduleEnabled = moduleEnabled.toCompose(),
            onModuleEnabled = onModuleEnabled.toCompose(),
            control = control.toCompose(),
            controlSelected = controlSelected.toCompose(),
            onControl = onControl.toCompose(),
            track = track.toCompose(),
            input = input.toCompose(),
            border = border.toCompose(),
            textShadow = textShadow.toCompose(),
        )
}
