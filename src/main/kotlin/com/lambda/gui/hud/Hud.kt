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

package com.lambda.gui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.lambda.Lambda.mc
import com.lambda.config.Config
import com.lambda.config.categories.HudCategory
import com.lambda.config.settings.comparable.boolSetting
import com.lambda.config.settings.complex.colorSetting
import com.lambda.config.settings.numeric.floatSetting
import com.lambda.core.Loadable
import com.lambda.gui.GuiScreen
import com.lambda.gui.component.Text
import com.lambda.gui.component.shadowed
import com.lambda.gui.core.observe
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.Type
import com.lambda.module.HudModule
import com.lambda.module.ModuleRegistry
import com.lambda.util.math.toCompose
import kotlin.math.roundToInt

private val HUD_TEXT_SHADOW = Color.Black.copy(alpha = 0.75f)
private val ELEMENT_PADDING = 1.dp

object HudConfig : Loadable, Config("HUD", HudCategory) {
    val showInScreens by boolSetting("Show In Screens", false, "Keep HUD elements drawn while a Minecraft screen (inventory, mod settings, ...) is open. The HUD editor always shows them.")
    val textColor by colorSetting("Text Color", java.awt.Color.WHITE, "Colour of HUD text")
    val scale by floatSetting("Scale", 1f, 0.5f..3f, 0.05f, "Size of HUD text")
}

internal object Hud {
    val modules: List<HudModule> by lazy { ModuleRegistry.modules.filterIsInstance<HudModule>() }

    val enabled: List<HudModule>
        get() = modules.filter { it.isEnabled }

    val shown: List<HudModule>
        get() {
            val screen = mc.currentScreen
            val covered = screen != null && screen !is GuiScreen && !HudConfig.showInScreens
            return if (mc.world == null || mc.options.hudHidden || covered) emptyList()
            else enabled
        }
}

@Composable
internal fun HudLayer(editor: HudEditorState? = null) {
    val shown by observe {
        if (editor != null) Hud.enabled
        else Hud.shown
    }
    Box(Modifier.fillMaxSize()) {
        shown.forEach { hud ->
            key(hud) { HudElement(hud, editor) }
        }
    }
}

@Composable
private fun HudElement(hud: HudModule, editor: HudEditorState?) {
    val anchor by observe { Offset(hud.anchorX, hud.anchorY) }
    val background by observe { hud.backgroundColor.value.toCompose() }
    val scale by observe { hud.scale }
    var empty by remember { mutableStateOf(false) }
    if (editor != null) DisposableEffect(hud) {
        onDispose { editor.bounds -= hud }
    }

    val density = LocalDensity.current
    val scaled = remember(density, scale) { Density(density.density * scale, density.fontScale) }
    Box(
        Modifier
            .layout { measurable, constraints ->
                val placeable =
                    measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                val roomX = (constraints.maxWidth - placeable.width).coerceAtLeast(0)
                val roomY = (constraints.maxHeight - placeable.height).coerceAtLeast(0)
                val dragged = editor?.shownPosition(hud)
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(
                        (dragged?.x ?: (roomX * anchor.x))
                            .roundToInt()
                            .coerceIn(0, roomX),
                        (dragged?.y ?: (roomY * anchor.y))
                            .roundToInt()
                            .coerceIn(0, roomY)
                    )
                }
            }
            .then(if (editor != null) Modifier.hudEditable(hud, editor) else Modifier)
            .clipToBounds()
            .background(background)
    ) {
        CompositionLocalProvider(LocalDensity provides scaled) {
            Box(Modifier.padding(ELEMENT_PADDING)) {
                Box(Modifier.onSizeChanged { empty = it.width == 0 || it.height == 0 }) { hud.Content() }
                if (editor != null && empty) HudText(hud.name, color = LambdaTheme.colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun HudText(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    val hudColor by observe { HudConfig.textColor.toCompose() }
    val scale by observe { HudConfig.scale }
    val size = Type.body.fontSize * scale
    Text(
        text,
        modifier,
        color ?: hudColor,
        Type.body.copy(
            fontSize = size,
            lineHeight = size * 1.25f
        ).shadowed(HUD_TEXT_SHADOW)
    )
}
