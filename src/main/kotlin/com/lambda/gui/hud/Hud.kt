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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lambda.Lambda.mc
import com.lambda.config.Config
import com.lambda.config.categories.HudCategory
import com.lambda.core.Loadable
import com.lambda.gui.GuiScreen
import com.lambda.gui.core.observe
import com.lambda.gui.render.ItemAtlas
import com.lambda.gui.render.ItemAtlas.drawItem
import com.lambda.gui.theme.Text
import com.lambda.gui.theme.Type
import com.lambda.gui.theme.shadowed
import com.lambda.gui.theme.toCompose
import com.lambda.module.HudModule
import com.lambda.module.ModuleRegistry
import kotlin.math.roundToInt
import net.minecraft.item.ItemStack

/** Settings shared by every HUD element, and by the editor that arranges them */
object HudConfig : Loadable, Config("HUD", HudCategory) {
    val showInScreens by setting("Show In Screens", false, "Keep HUD elements drawn while a Minecraft screen (inventory, mod settings, ...) is open. The Lambda GUI always shows them.")
    val textColor by setting("Text Color", java.awt.Color.WHITE, "Colour of HUD text")
    val scale by setting("Scale", 1f, 0.5f..3f, 0.05f, "Size of HUD text")
}

internal object Hud {
    val modules: List<HudModule> by lazy { ModuleRegistry.modules.filterIsInstance<HudModule>() }

    /**
     * The elements to draw right now. None outside a world or with the HUD hidden (F1), and none
     * behind a Minecraft screen unless [HudConfig.showInScreens]; Lambda's own screens always show them.
     */
    val shown: List<HudModule>
        get() {
            val screen = mc.currentScreen
            val covered = screen != null && screen !is GuiScreen && !HudConfig.showInScreens
            return if (mc.world == null || mc.options.hudHidden || covered) emptyList() else modules.filter { it.isEnabled }
        }
}

/** The shadow under HUD text, as dark as Minecraft's own: the text lies on the world, not on the interface */
private val HudTextShadow = Color.Black.copy(alpha = 0.75f)

/**
 * Space between the edge of an element and its content: one pixel of the Minecraft font, the
 * least that keeps text off the edge of a background and leaves the editor's outline a place.
 */
private val ElementPadding = 1.dp

/**
 * Every enabled HUD element, at its place on the screen. With an [editor] the elements can be
 * dragged around and right-clicked; without one they are only drawn.
 */
@Composable
internal fun HudLayer(editor: HudEditorState? = null) {
    val shown by observe { Hud.shown }
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
    if (editor != null) DisposableEffect(hud) {
        onDispose { editor.bounds -= hud }
    }

    Box(
        Modifier
            // The element is measured freely and then placed by its anchor: the fraction of
            // the room it has to move in, so it stays against the edge it was docked to.
            // Whatever the anchor or a drag says, all of it stays on the screen: it is measured
            // no larger than the screen, and placed no further than the room it leaves.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                val roomX = (constraints.maxWidth - placeable.width).coerceAtLeast(0)
                val roomY = (constraints.maxHeight - placeable.height).coerceAtLeast(0)
                val dragged = editor?.shownPosition(hud)
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(
                        (dragged?.x ?: (roomX * anchor.x)).roundToInt().coerceIn(0, roomX),
                        (dragged?.y ?: (roomY * anchor.y)).roundToInt().coerceIn(0, roomY)
                    )
                }
            }
            .then(if (editor != null) Modifier.hudEditable(hud, editor) else Modifier)
            // An element with more to show than fits the screen is cut off at its own edge
            .clipToBounds()
            .background(background)
            .padding(ElementPadding)
    ) {
        hud.Content()
    }
}

/** HUD text: the HUD's colour unless given one, its size, and a shadow to stay readable over the world */
@Composable
fun HudText(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    val hudColor by observe { HudConfig.textColor.toCompose() }
    val scale by observe { HudConfig.scale }
    val size = Type.body.fontSize * scale
    Text(text, modifier, color ?: hudColor, Type.body.copy(fontSize = size, lineHeight = size * 1.25f).shadowed(HudTextShadow))
}

/**
 * [stack] as Minecraft draws it in an inventory slot, with its damage bar and stack count.
 * Draws nothing for an empty stack.
 *
 * Minecraft only ever draws an item at a whole multiple of its 16 pixels, which is what keeps
 * the pixel art crisp. So does this: the icon takes the multiple nearest to [size].
 */
@Composable
fun ItemIcon(stack: ItemStack, size: Dp, modifier: Modifier = Modifier) {
    val slot = remember { ItemAtlas.Slot() }
    val density = LocalDensity.current
    val pixels = with(density) { ItemAtlas.ITEM * (size.toPx() / ItemAtlas.ITEM).roundToInt().coerceAtLeast(1) }
    SideEffect {
        slot.stack = stack
        slot.size = pixels
    }
    DisposableEffect(slot) {
        ItemAtlas.add(slot)
        onDispose { ItemAtlas.remove(slot) }
    }
    Spacer(modifier.size(with(density) { pixels.toDp() }).drawBehind { drawItem(slot) })
}
