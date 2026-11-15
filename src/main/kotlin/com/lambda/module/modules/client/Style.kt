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
    val dimBackground by setting("Dim Background", true, "Darkens what is behind the click GUI while it is open, whatever NoRender says about GUI shadows")
    val dimStrength by setting("Dim Strength", 0.6f, 0.05f..1f, 0.05f, "How dark the background gets. Minecraft's own menus use about 0.8") { dimBackground }

    val blur by setting("Blur", true, "Blurs what is behind windows for a frosted glass look")
    val blurRadius by setting("Blur Radius", 4f, 1f..32f, 1f, "Strength of the blur") { blur }

    val outlines by setting("Outlines", true, "Draws a thin border around windows, panels and menus. Off for the look of pure glass")

    val glow by setting("Glow", true, "Surrounds the click GUI with a glow")
    val glowRadius by setting("Glow Radius", 17f, 1f..100f, 1f) { glow }
    val glowIntensity by setting("Glow Intensity", 0.3f, 0.1f..3f, 0.1f) { glow }
    val glowColorA by setting("Glow Color 1", Color(176, 0, 70)) { glow }
    val glowColorB by setting("Glow Color 2", Color(255, 94, 128)) { glow }

    // Both themes are built from the colours of Lambda's logo and nothing else: its light rose
    // (255, 94, 128), its red (214, 55, 88), its crimson (176, 0, 70), and the two shades it is
    // shadowed with, (107, 27, 44) and (88, 0, 35). The glow above runs from crimson to rose.
    @Group(DARK_THEME_GROUP) private val darkTheme by configBlock(
        PaletteSettings(
            this,
            primary = Color(214, 55, 88),
            secondary = Color(255, 94, 128),
            surface = Color(18, 0, 7, 80),
            panel = Color(16, 12, 14, 140),
            inset = Color(0, 0, 0, 90),
            onSurface = Color(240, 240, 245),
            onSurfaceVariant = Color(140, 140, 150),
            header = Color(176, 0, 70, 240),
            onHeader = Color(240, 240, 245),
            // A step under the header: the name of a category stays the strongest thing in its window
            moduleEnabled = Color(205, 10, 80, 175),
            onModuleEnabled = Color(240, 240, 245),
            // Controls are filled in the colour of a slider's fill and a ticked box, so all of them read as one kind of thing
            control = Color(214, 55, 88, 240),
            controlMenu = Color(48, 16, 23),
            controlSelected = Color(88, 32, 45),
            onControl = Color(255, 255, 255),
            // Wells cut into the panel, darker than any glass they sit on so they never blend into it
            track = Color(22, 18, 20, 220),
            input = Color(14, 11, 13, 220),
            border = Color(140, 0, 56, 235),
            textShadow = Color(0, 0, 0, 180),
        )
    )

    // Light glass has to be nearly solid: dark text over whatever the game shows through it
    // cannot be read, and a shadow under dark text only smears it
    @Group(LIGHT_THEME_GROUP) private val lightTheme by configBlock(
        PaletteSettings(
            this,
            primary = Color(214, 55, 88),
            secondary = Color(176, 0, 70),
            surface = Color(252, 246, 247, 205),
            panel = Color(247, 244, 245, 222),
            inset = Color(120, 0, 40, 34),
            onSurface = Color(40, 14, 20),
            onSurfaceVariant = Color(128, 102, 108),
            header = Color(214, 55, 88, 242),
            onHeader = Color(255, 250, 251),
            moduleEnabled = Color(250, 180, 198, 190),
            onModuleEnabled = Color(120, 0, 48),
            control = Color(214, 55, 88, 242),
            controlMenu = Color(246, 232, 235),
            controlSelected = Color(236, 198, 208),
            onControl = Color(40, 14, 20),
            track = Color(214, 196, 201, 228),
            input = Color(255, 255, 255, 235),
            border = Color(216, 134, 150, 238),
            textShadow = Color(0, 0, 0, 0),
        )
    )

    val palette: Palette
        get() = when (themeMode) {
            ThemeMode.Dark -> darkTheme
            ThemeMode.Light -> lightTheme
            ThemeMode.Auto -> if (SystemAppearance.isDark) darkTheme else lightTheme
        }.toPalette()

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
) : ConfigBlock {
    private val primary by c.setting("Primary", primary, "Accent colour of sliders, checkboxes and selections")
    private val secondary by c.setting("Secondary", secondary)
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
    private val controlSelected by c.setting("Control Selected", controlSelected)
    private val onControl by c.setting("On Control", onControl)
    private val track by c.setting("Slider Track", track)
    private val input by c.setting("Text Field", input)
    private val border by c.setting("Border", border)
    private val textShadow by c.setting("Text Shadow", textShadow)

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
