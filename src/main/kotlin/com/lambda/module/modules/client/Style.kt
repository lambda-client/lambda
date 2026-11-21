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

package com.lambda.module.modules.client

import com.lambda.config.Config
import com.lambda.config.ConfigBlock
import com.lambda.config.Group
import com.lambda.config.settings.appliedOnRelease
import com.lambda.gui.theme.GuiFont
import com.lambda.gui.theme.GuiInterrupt
import com.lambda.gui.theme.GuiTransition
import com.lambda.gui.theme.Palette
import com.lambda.module.Module
import com.lambda.module.ModuleTag
import com.lambda.util.NamedEnum
import com.lambda.util.math.toCompose
import org.jetbrains.skiko.SystemTheme
import org.jetbrains.skiko.currentSystemTheme
import java.awt.Color

object Style : Module(
    name = "Style",
    description = "How Lambda's interface looks",
    tag = ModuleTag.CLIENT,
    alwaysEnabled = true,
) {
    private const val BACKDROP_GROUP = "Backdrop"
    private const val SHAPE_GROUP = "Shape"
    private const val LAYOUT_GROUP = "Layout"
    private const val MOTION_GROUP = "Motion"
    private const val DARK_THEME_GROUP = "Dark Theme"
    private const val LIGHT_THEME_GROUP = "Light Theme"

    private enum class ThemeMode(override val displayName: String) : NamedEnum {
        Auto("Auto"),
        Dark("Dark"),
        Light("Light"),
    }

    private val themeMode by setting("Theme", ThemeMode.Dark, "Auto follows the theme of your operating system")
    val font by setting("Font", GuiFont.Minecraft, "The Minecraft font is sharpest where the interface is a whole number of times its natural size: at 1080p and 2160p with a scale of 1")
    val fontSize by setting("Font Size", 0.85f, 0.5f..1.2f, 0.05f, "Size of text, as a share of the size the font reads best at. Rows grow to fit larger text. The Minecraft font is only sharp at 1")
    val scale by setting("Scale", 1f, 0.5f..1.5f, 0.05f, "Size of the interface. Dragged, it is applied when the button is let go. No larger than leaves room on the screen for every window and dialog").appliedOnRelease()

    @Group(BACKDROP_GROUP) val dimBackground by setting("Dim Background", true, "Darkens what is behind the click GUI while it is open, whatever NoRender says about GUI shadows")
    @Group(BACKDROP_GROUP) val dimStrength by setting("Dim Strength", 0.6f, 0.05f..1f, 0.05f, "How dark the background gets. Minecraft's own menus use about 0.8") { dimBackground }
    @Group(BACKDROP_GROUP) val blur by setting("Blur", true, "Blurs what is behind windows for a frosted glass look")
    @Group(BACKDROP_GROUP) val blurRadius by setting("Blur Radius", 4f, 1f..32f, 1f, "Strength of the blur") { blur }
    @Group(BACKDROP_GROUP) val layerFade by setting("Layer Fade", 0.5f, 0f..1f, 0.05f, "How far the interface fades out underneath a panel, menu or window opened over it, letting the game show there instead so the two do not run together") { blur }
    @Group(BACKDROP_GROUP) val glow by setting("Glow", false, "Surrounds the click GUI with a glow, in the two glow colours of the theme in use")
    @Group(BACKDROP_GROUP) val glowRadius by setting("Glow Radius", 17f, 1f..100f, 1f) { glow }
    @Group(BACKDROP_GROUP) val glowIntensity by setting("Glow Intensity", 0.3f, 0.1f..3f, 0.1f) { glow }

    @Group(SHAPE_GROUP) val windowRounding by setting("Window Rounding", 3f, 0f..12f, 0.5f, "Corners of the category windows, and of the band of tabs that is made like their headers. Square at 0")
    @Group(SHAPE_GROUP) val panelRounding by setting("Panel Rounding", 5f, 0f..16f, 0.5f, "Corners of a module's settings panel, of dialogs and of the colour picker")
    @Group(SHAPE_GROUP) val menuRounding by setting("Menu Rounding", 3f, 0f..12f, 0.5f, "Corners of menus, dropdowns, search results and tooltips")
    @Group(SHAPE_GROUP) val controlRounding by setting("Control Rounding", 2f, 0f..8f, 0.5f, "Corners of sliders, chips, buttons, fields and group headings. Tick boxes get half of it")
    @Group(SHAPE_GROUP) val outlines by setting("Outlines", true, "Draws a thin border around windows, panels and menus. Off for the look of pure glass")
    @Group(SHAPE_GROUP) val outlineWidth by setting("Outline Width", 0.5f, 0.5f..3f, 0.5f, "Thickness of the outlines. 0.5 is one pixel at 1080p") { outlines }
    @Group(SHAPE_GROUP) val shadows by setting("Shadows", true, "Casts a soft shadow from panels, menus and dialogs onto the interface beneath them")
    @Group(SHAPE_GROUP) val emboss by setting("Emboss", false, "Lights each module faintly from above, so the list feels less flat")

    @Group(LAYOUT_GROUP) val elementHeight by setting("Element Height", 11.5f, 10f..24f, 0.5f, "Height of a module in its category and of a row of settings, never less than its text needs")
    @Group(LAYOUT_GROUP) val windowWidth by setting("Window Width", 90f, 60f..160f, 5f, "Width of the category windows. Names too long for it slide under the pointer")
    @Group(LAYOUT_GROUP) val settingsWidth by setting("Settings Width", 180f, 130f..320f, 10f, "The widest a module's settings may get. Values too long for it slide under the pointer")
    @Group(LAYOUT_GROUP) val windowGap by setting("Window Gap", 2f, 0f..20f, 1f, "Room between the category windows in their row, and what one snaps to beside another")
    @Group(LAYOUT_GROUP) val windowTop by setting("Window Top", 94f, 20f..300f, 2f, "How far down the screen the row of category windows starts, for those that have not been moved")
    @Group(LAYOUT_GROUP) val modulePadding by setting("Module Padding", 4f, 0f..12f, 0.5f, "Room to either side of a module's name, and of its category's")
    @Group(LAYOUT_GROUP) val panelPadding by setting("Panel Padding", 4f, 0f..12f, 0.5f, "Room around the settings in a module's settings panel")
    @Group(LAYOUT_GROUP) val settingSpacing by setting("Setting Spacing", 1f, 0f..6f, 0.5f, "Room between one setting and the next")
    @Group(LAYOUT_GROUP) val groupIndent by setting("Group Indent", 5f, 0f..12f, 0.5f, "How far the settings of a group are set in behind its bar")
    @Group(LAYOUT_GROUP) val inlineSettings by setting("Inline Settings", false, "A module's settings drop down under it inside its window, as wide as the window and pushing the modules below it down, instead of popping out beside it")
    @Group(LAYOUT_GROUP) val multipleSettings by setting("Multiple Settings", false, "Lets the settings of several modules be open at once, where otherwise opening one closes the last. A click beside them closes them all either way")

    @Group(MOTION_GROUP) val animations by setting("Animations", true, "Unfolds, slides and fades the interface. Off, everything but scrolling is where it is going at once")
    @Group(MOTION_GROUP) val animationSpeed by setting("Animation Speed", 1f, 0.25f..4f, 0.25f, "How fast the interface animates, as a multiple of what it is made with") { animations }
    @Group(MOTION_GROUP) val openAnimation by setting("Open Animation", true, "Animates the click GUI in when it opens") { animations }
    @Group(MOTION_GROUP) val openStyle by setting("Open Style", setOf(GuiTransition.Unfold), GuiTransition.entries.toSet(), "What the click GUI does as it comes in, any of them together: its windows unfold out of their headers, and all of it fades, slides down or zooms in") { animations && openAnimation }
    @Group(MOTION_GROUP) val closeAnimation by setting("Close Animation", true, "Animates the click GUI out when it closes. The game has the mouse and keyboard back at once: the animation only plays over it") { animations }
    @Group(MOTION_GROUP) val closeStyle by setting("Close Style", setOf(GuiTransition.Unfold, GuiTransition.Fade), GuiTransition.entries.toSet(), "What the click GUI does as it goes out, any of them together: its windows fold into their headers, and all of it fades, slides up or zooms out") { animations && closeAnimation }
    @Group(MOTION_GROUP) val interruptedAnimation by setting("Interrupted Animation", GuiInterrupt.Reverse, "What happens when the click GUI is opened or closed again before its animation is done: it turns around from where it is, or skips to the end and plays the other one in full") { animations && (openAnimation || closeAnimation) }
    @Group(MOTION_GROUP) val windowAnimations by setting("Window Animations", true, "Category windows unfold out of their headers") { animations }
    @Group(MOTION_GROUP) val panelAnimations by setting("Panel Animations", true, "Settings panels, menus, dropdowns and search results unfold out of what opened them") { animations }
    @Group(MOTION_GROUP) val pageAnimations by setting("Page Animations", true, "Changing the tab or the page of a settings panel slides the new one in") { animations }
    @Group(MOTION_GROUP) val smoothScrolling by setting("Smooth Scrolling", true, "The mouse wheel sets a list off fast and lets it glide to rest. On its own: it works with Animations off, and at its own pace")
    @Group(MOTION_GROUP) val scrollSpeed by setting("Scroll Speed", 1f, 0.25f..3f, 0.25f, "How far a notch of the mouse wheel takes a list, as a multiple of three rows")
    @Group(MOTION_GROUP) val tooltipDelay by setting("Tooltip Delay", 500, 0..2000, 50, "How long the pointer rests on something before its description shows", " ms")

    @Group(DARK_THEME_GROUP) private val darkTheme by configBlock(
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

    @Group(LIGHT_THEME_GROUP) private val lightTheme by configBlock(
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
    private val primary by c.setting("Primary", primary, "Accent colour: the fill of sliders and ticked boxes")
    private val secondary by c.setting("Secondary", secondary, "Second accent: what is chosen among options, as in a menu or a row of pages")
    private val surface by c.setting("Surface", surface, "Fill of windows. Lower its opacity to see more of the blur")
    private val panel by c.setting("Panel", panel, "Fill of settings panels, menus and dialogs")
    private val inset by c.setting("Inset", inset, "Shade behind settings that open under their module, and over the module whose settings are open beside it")
    private val onSurface by c.setting("On Surface", onSurface, "Text on windows")
    private val onSurfaceVariant by c.setting("On Surface Variant", onSurfaceVariant, "Subdued text on windows")
    private val header by c.setting("Header", header)
    private val onHeader by c.setting("On Header", onHeader)
    private val moduleEnabled by c.setting("Module Enabled", moduleEnabled)
    private val onModuleEnabled by c.setting("On Module Enabled", onModuleEnabled)
    private val control by c.setting("Control", control, "Fill of dropdowns, buttons and key caps")
    private val controlSelected by c.setting("Control Selected", controlSelected, "Fill of the entry of a menu or list that is chosen or under the pointer")
    private val onControl by c.setting("On Control", onControl)
    private val track by c.setting("Slider Track", track)
    private val input by c.setting("Text Field", input)
    private val border by c.setting("Border", border)
    private val textShadow by c.setting("Text Shadow", textShadow)
    val glowA by c.setting("Glow Color 1", glowA, "Where the glow around the click GUI starts") { Style.glow }
    val glowB by c.setting("Glow Color 2", glowB, "Where the glow around the click GUI ends") { Style.glow }

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
