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
import com.lambda.gui.theme.Palette
import com.lambda.gui.theme.toCompose
import com.lambda.module.Module
import com.lambda.module.ModuleTag
import com.lambda.util.NamedEnum
import org.jetbrains.skiko.SystemTheme
import org.jetbrains.skiko.currentSystemTheme
import java.awt.Color

object Style : Module(
    name = "Style",
    description = "How Lambda's interface looks",
    tag = ModuleTag.CLIENT,
    alwaysEnabled = true,
) {
    private const val DARK_THEME_GROUP = "Dark Theme"
    private const val LIGHT_THEME_GROUP = "Light Theme"
    private const val SHAPE_GROUP = "Shape"
    private const val MOTION_GROUP = "Motion"
    private const val SPACING_GROUP = "Spacing"

    private enum class ThemeMode(override val displayName: String) : NamedEnum {
        Auto("Auto"),
        Dark("Dark"),
        Light("Light"),
    }

    private val themeMode by setting("Theme", ThemeMode.Auto, "Auto follows the theme of your operating system")
    val font by setting("Font", GuiFont.Minecraft, "The Minecraft font is sharpest where the interface is a whole number of times its natural size: at 1080p and 2160p with a scale of 1")
    var scale by setting("Scale", 1f, 0.5f..1.5f, 0.05f, "Size of the interface. Dragged, it is applied when the button is let go. No larger than leaves room on the screen for every window and dialog").appliedOnRelease()
    var fontSize by setting("Font Size", 0.85f, 0.5f..1.2f, 0.05f, "Size of text, as a share of the size the font reads best at. Rows grow to fit larger text. The Minecraft font is only sharp at 1")
    var elementHeight by setting("Element Height", 11.5f, 10f..24f, 0.5f, "Height of a module in its category and of a row of settings, never less than its text needs")
    val dimBackground by setting("Dim Background", true, "Darkens what is behind the click GUI while it is open, whatever NoRender says about GUI shadows")
    val dimStrength by setting("Dim Strength", 0.6f, 0.05f..1f, 0.05f, "How dark the background gets. Minecraft's own menus use about 0.8") { dimBackground }

    val blur by setting("Blur", true, "Blurs what is behind windows for a frosted glass look")
    val blurRadius by setting("Blur Radius", 4f, 1f..32f, 1f, "Strength of the blur") { blur }

    val layerFade by setting("Layer Fade", 0.5f, 0f..1f, 0.05f, "How far the interface fades out underneath a panel, menu or window opened over it, letting the game show there instead so the two do not run together") { blur }
    val outlines by setting("Outlines", true, "Draws a thin border around windows, panels and menus. Off for the look of pure glass")
    val emboss by setting("Emboss", false, "Lights each module faintly from above, so the list feels less flat")

    var inlineSettings by setting("Inline Settings", false, "A module's settings drop down under it inside its window, as wide as the window and pushing the modules below it down, instead of popping out beside it")
    var multipleSettings by setting("Multiple Settings", false, "Lets the settings of several modules be open at once, where otherwise opening one closes the last. A click beside them closes them all either way")

    var glow by setting("Glow", false, "Surrounds the click GUI with a glow, in the two glow colours of the theme in use")
    val glowRadius by setting("Glow Radius", 17f, 1f..100f, 1f) { glow }
    val glowIntensity by setting("Glow Intensity", 0.3f, 0.1f..3f, 0.1f) { glow }

    @Group(SHAPE_GROUP) var windowRounding by setting("Window Rounding", 3f, 0f..12f, 0.5f, "Corners of the category windows, and of the band of tabs that is made like their headers. Square at 0")
    @Group(SHAPE_GROUP) var panelRounding by setting("Panel Rounding", 5f, 0f..16f, 0.5f, "Corners of a module's settings panel, of dialogs and of the colour picker")
    @Group(SHAPE_GROUP) val menuRounding by setting("Menu Rounding", 3f, 0f..12f, 0.5f, "Corners of menus, dropdowns, search results and tooltips")
    @Group(SHAPE_GROUP) val controlRounding by setting("Control Rounding", 2f, 0f..8f, 0.5f, "Corners of sliders, chips, buttons, fields and group headings. Tick boxes get half of it")
    @Group(SHAPE_GROUP) var outlineWidth by setting("Outline Width", 0.5f, 0.5f..3f, 0.5f, "Thickness of the outlines. 0.5 is one pixel at 1080p") { outlines }
    @Group(SHAPE_GROUP) val shadows by setting("Shadows", true, "Casts a soft shadow from panels, menus and dialogs onto the interface beneath them")
    @Group(SHAPE_GROUP) var windowWidth by setting("Window Width", 90f, 60f..160f, 5f, "Width of the category windows. Names too long for it slide under the pointer")
    @Group(SHAPE_GROUP) val settingsWidth by setting("Settings Width", 180f, 130f..320f, 10f, "The widest a module's settings may get. Values too long for it slide under the pointer")

    @Group(SPACING_GROUP) var windowGap by setting("Window Gap", 2f, 0f..20f, 1f, "Room between the category windows in their row, and what one snaps to beside another")
    @Group(SPACING_GROUP) var windowTop by setting("Window Top", 94f, 20f..300f, 2f, "How far down the screen the row of category windows starts, for those that have not been moved")
    @Group(SPACING_GROUP) var modulePadding by setting("Module Padding", 4f, 0f..12f, 0.5f, "Room to either side of a module's name, and of its category's")
    @Group(SPACING_GROUP) var panelPadding by setting("Panel Padding", 4f, 0f..12f, 0.5f, "Room around the settings in a module's settings panel")
    @Group(SPACING_GROUP) var settingSpacing by setting("Setting Spacing", 1f, 0f..6f, 0.5f, "Room between one setting and the next")
    @Group(SPACING_GROUP) var groupIndent by setting("Group Indent", 5f, 0f..12f, 0.5f, "How far the settings of a group are set in behind its bar")

    @Group(MOTION_GROUP) var animations by setting("Animations", true, "Unfolds, slides and fades the interface. Off, everything but scrolling is where it is going at once")
    @Group(MOTION_GROUP) val animationSpeed by setting("Animation Speed", 1f, 0.25f..4f, 0.25f, "How fast the interface animates, as a multiple of what it is made with") { animations }
    @Group(MOTION_GROUP) val windowAnimations by setting("Window Animations", true, "Category windows unfold out of their headers") { animations }
    @Group(MOTION_GROUP) var panelAnimations by setting("Panel Animations", true, "Settings panels, menus, dropdowns and search results unfold out of what opened them") { animations }
    @Group(MOTION_GROUP) val pageAnimations by setting("Page Animations", true, "Changing the tab or the page of a settings panel slides the new one in") { animations }
    @Group(MOTION_GROUP) val smoothScrolling by setting("Smooth Scrolling", true, "The mouse wheel sets a list off fast and lets it glide to rest. On its own: it works with Animations off, and at its own pace")
    @Group(MOTION_GROUP) val scrollSpeed by setting("Scroll Speed", 1f, 0.25f..3f, 0.25f, "How far a notch of the mouse wheel takes a list, as a multiple of three rows")
    @Group(MOTION_GROUP) val tooltipDelay by setting("Tooltip Delay", 500, 0..2000, 50, "How long the pointer rests on something before its description shows", " ms")

    /** The colours the glow runs between, which are the theme's: what suits dark glass is lost around light */
    val glowColorA: Color get() = theme.glowA
    val glowColorB: Color get() = theme.glowB

    @Group(DARK_THEME_GROUP) private val darkTheme by configBlock(
        PaletteSettings(
            this,
            // A shade lighter than the header and a shade less full, so it is plain to see without being loud: a panel is
            // read by its band of tabs, and what is set in it is not to be taken for a module that is on
            primary = Color(194, 86, 114),
            secondary = Color(255, 168, 158),
            surface = Color(60, 48, 52, 165),
            panel = Color(48, 38, 42, 200),
            inset = Color(0, 0, 0, 190),
            onSurface = Color(246, 236, 239),
            onSurfaceVariant = Color(172, 152, 159),
            header = Color(190, 62, 98),
            onHeader = Color(255, 246, 248),
            // A shade of the header, as strong and a good step darker: greyed and left as light it turns to a dusty pink
            moduleEnabled = Color(138, 46, 72),
            onModuleEnabled = Color(255, 240, 244),
            // Every control is the one rose: a chip, a button and a key cap are filled as a slider
            // and a ticked box are, so that whatever can be set looks like one kind of thing
            control = Color(194, 86, 114),
            controlMenu = Color(48, 38, 42),
            controlSelected = Color(81, 65, 71),
            onControl = Color(246, 236, 239),
            // Wells cut into the panel, darker than any glass they sit on so they never blend into it
            track = Color(34, 27, 30, 230),
            input = Color(29, 23, 25, 230),
            border = Color(114, 93, 100, 235),
            textShadow = Color(0, 0, 0, 150),
            // From the rose of the headers to the salmon, the two ends of the theme's range
            glowA = Color(190, 62, 98),
            glowB = Color(255, 168, 158),
        )
    )

    // Light glass has to be nearly solid: dark text over whatever the game shows through it
    // cannot be read, and a shadow under dark text only smears it. Its tiers run the other way,
    // darker for what is deeper, and its rose is the crimson of the logo, which holds on white.
    @Group(LIGHT_THEME_GROUP) private val lightTheme by configBlock(
        PaletteSettings(
            this,
            primary = Color(214, 55, 88),
            secondary = Color(196, 58, 52),
            surface = Color(246, 240, 243, 200),
            panel = Color(240, 233, 237, 225),
            inset = Color(120, 0, 40, 68),
            onSurface = Color(64, 44, 54),
            onSurfaceVariant = Color(128, 108, 117),
            header = Color(214, 55, 88, 242),
            onHeader = Color(255, 250, 251),
            // Filled a step under the header with its text in white, as in the dark theme: a tint of
            // pink under crimson text was too faint to pick the enabled out of a list at a glance
            moduleEnabled = Color(226, 104, 130),
            onModuleEnabled = Color(255, 250, 251),
            // The rose of the fill of a slider, which is the accent thinned for the dark text over it
            control = Color(214, 114, 136),
            controlMenu = Color(240, 233, 237),
            controlSelected = Color(228, 210, 218),
            onControl = Color(64, 44, 54),
            track = Color(214, 203, 208, 220),
            input = Color(255, 255, 255, 200),
            border = Color(190, 172, 180, 238),
            textShadow = Color(0, 0, 0, 0),
            // From the rose of the headers to a paler one: around light glass a glow has to stay light itself
            glowA = Color(214, 55, 88),
            glowB = Color(255, 150, 172),
        )
    )

    private val theme: PaletteSettings
        get() = when (themeMode) {
            ThemeMode.Dark -> darkTheme
            ThemeMode.Light -> lightTheme
            ThemeMode.Auto -> if (SystemAppearance.isDark) darkTheme else lightTheme
        }

    val palette: Palette get() = theme.toPalette()

    /** Asking the operating system is not free, so the answer is reused for a moment */
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
    controlMenu: Color,
    controlSelected: Color,
    onControl: Color,
    track: Color,
    input: Color,
    border: Color,
    textShadow: Color,
    glowA: Color,
    glowB: Color,
) : ConfigBlock {
    private val primary by c.setting("Primary", primary, "Accent colour: the fill of sliders, ticked boxes, what is chosen")
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
    private val control by c.setting("Control", control, "Fill of dropdowns, buttons and unchecked boxes")
    private val controlMenu by c.setting("Control Menu", controlMenu, "Fill of an open dropdown's list")
    private val controlSelected by c.setting("Control Selected", controlSelected, "Fill of the entry of a menu or list that is chosen or under the pointer")
    private val onControl by c.setting("On Control", onControl)
    private val track by c.setting("Slider Track", track)
    private val input by c.setting("Text Field", input)
    private val border by c.setting("Border", border)
    private val textShadow by c.setting("Text Shadow", textShadow)
    val glowA by c.setting("Glow Color 1", glowA, "Where the glow around the click GUI starts") { Style.glow }
    val glowB by c.setting("Glow Color 2", glowB, "Where the glow around the click GUI ends") { Style.glow }

    fun toPalette() = Palette(
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
        controlMenu = controlMenu.toCompose(),
        controlSelected = controlSelected.toCompose(),
        onControl = onControl.toCompose(),
        track = track.toCompose(),
        input = input.toCompose(),
        border = border.toCompose(),
        textShadow = textShadow.toCompose(),
    )
}
