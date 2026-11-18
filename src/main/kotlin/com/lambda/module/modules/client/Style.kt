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

    private enum class ThemeMode(override val displayName: String) : NamedEnum {
        Auto("Auto"),
        Dark("Dark"),
        Light("Light"),
    }

    private val themeMode by setting("Theme", ThemeMode.Auto, "Auto follows the theme of your operating system")
    val font by setting("Font", GuiFont.Minecraft, "The Minecraft font is sharpest where the interface is a whole number of times its natural size: at 1080p and 2160p with a scale of 1")
    val scale by setting("Scale", 1f, 0.5f..2f, 0.05f, "Size of the interface")
    var fontSize by setting("Font Size", 0.85f, 0.5f..1.2f, 0.05f, "Size of text, as a share of the size the font reads best at. Rows grow to fit larger text. The Minecraft font is only sharp at 1")
    var elementHeight by setting("Element Height", 11.5f, 10f..24f, 0.5f, "Height of a module in its category and of a row of settings, never less than its text needs")
    val dimBackground by setting("Dim Background", true, "Darkens what is behind the click GUI while it is open, whatever NoRender says about GUI shadows")
    val dimStrength by setting("Dim Strength", 0.6f, 0.05f..1f, 0.05f, "How dark the background gets. Minecraft's own menus use about 0.8") { dimBackground }

    val blur by setting("Blur", true, "Blurs what is behind windows for a frosted glass look")
    val blurRadius by setting("Blur Radius", 4f, 1f..32f, 1f, "Strength of the blur") { blur }

    val layerFade by setting("Layer Fade", 0.5f, 0f..1f, 0.05f, "How far the interface fades out underneath a panel, menu or window opened over it, letting the game show there instead so the two do not run together") { blur }
    val outlines by setting("Outlines", true, "Draws a thin border around windows, panels and menus. Off for the look of pure glass")
    val emboss by setting("Emboss", false, "Lights each module faintly from above, so the list feels less flat")

    var glow by setting("Glow", false, "Surrounds the click GUI with a glow, in the two glow colours of the theme in use")
    val glowRadius by setting("Glow Radius", 17f, 1f..100f, 1f) { glow }
    val glowIntensity by setting("Glow Intensity", 0.3f, 0.1f..3f, 0.1f) { glow }

    /** The colours the glow runs between, which are the theme's: what suits dark glass is lost around light */
    val glowColorA: Color get() = theme.glowA
    val glowColorB: Color get() = theme.glowB

    // Both themes are Lambda's rose and nothing else, structured the way Catppuccin structures
    // its own. Most of what is on screen is a neutral warmed with a tenth of rose, stepped
    // in tiers: wells, panels, windows, controls, borders. Kept that light and well clear of
    // black it reads as a warm grey, where a darker one with as much rose in it turns to wine.
    //
    // Over it the rose comes in a few pastel variants, each with one job, which shift in hue as
    // they do in lightness, the way a colour does in light: deeper ones lean to crimson, paler
    // ones to salmon. The deepest fills a header, a softer tone of it an enabled module. The
    // pale rose marks what is on or filled in a control, and the salmon what is chosen among
    // options. Fills of rose are opaque, as glass in a hue takes on whatever is behind it.
    @Group(DARK_THEME_GROUP) private val darkTheme by configBlock(
        PaletteSettings(
            this,
            primary = Color(250, 140, 164),
            secondary = Color(255, 168, 158),
            surface = Color(60, 48, 52, 175),
            panel = Color(48, 38, 42, 225),
            inset = Color(0, 0, 0, 90),
            onSurface = Color(246, 236, 239),
            onSurfaceVariant = Color(172, 152, 159),
            header = Color(190, 62, 98),
            onHeader = Color(255, 246, 248),
            // A tone of the header, greyer and a step down: the name of a category stays the strongest thing in its window
            moduleEnabled = Color(160, 76, 102),
            onModuleEnabled = Color(255, 240, 244),
            // Controls are a tier above anything they sit on, and say what they hold in plain text
            control = Color(92, 75, 81),
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
            inset = Color(120, 0, 40, 34),
            onSurface = Color(64, 44, 54),
            onSurfaceVariant = Color(128, 108, 117),
            header = Color(214, 55, 88, 242),
            onHeader = Color(255, 250, 251),
            moduleEnabled = Color(243, 217, 225),
            onModuleEnabled = Color(176, 0, 70),
            control = Color(216, 202, 209),
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
    private val inset by c.setting("Inset", inset, "Shade over the module whose settings are open, which looks pressed in")
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
