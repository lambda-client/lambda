/*
 * Copyright 2025 Lambda
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

package com.lambda.module.modules.render

import com.lambda.Lambda.mc
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.tooltip.TooltipComponent
import net.minecraft.client.render.MapRenderState
import net.minecraft.client.render.RenderLayer
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.MapIdComponent
import net.minecraft.item.FilledMapItem
import net.minecraft.item.ItemStack
import net.minecraft.item.map.MapState
import net.minecraft.item.tooltip.TooltipData
import net.minecraft.util.Identifier


object MapPreview : Module(
    name = "MapPreview",
    description = "Preview maps in your inventory",
    defaultTags = setOf(ModuleTag.RENDER)
) {
    private val background = Identifier.ofVanilla("textures/map/map_background.png")

    class MapComponent(val stack: ItemStack) : TooltipData, TooltipComponent {
        val state: MapState?
            get() = FilledMapItem.getMapState(stack, mc.world)

        val mapId: MapIdComponent?
            get() = stack.getOrDefault(DataComponentTypes.MAP_ID, null)

        override fun drawItems(textRenderer: TextRenderer, x: Int, y: Int, width: Int, height: Int, context: DrawContext) {
            mapId?.let { id ->
                // Values taken from net.minecraft.client.render.item.HeldItemRenderer.renderFirstPersonMap
                val state = state ?: return
                val matrices = context.matrices

                matrices.push()
                matrices.translate(x + 4.0, y + 4.0, 500.0)
                matrices.scale(0.7f, 0.7f, 1f)

                RenderSystem.enableBlend()
                context.drawTexture(RenderLayer::getGuiTextured, background, -7, -7, 0f, 0f, 142, 142, 142, 142)

                matrices.translate(0.0, 0.0, 1.0)

                val renderState = MapRenderState()
                mc.mapRenderer.update(id, state, renderState)
                context.draw { mc.mapRenderer.draw(renderState, matrices, it, true, 0xF000F0) }
                matrices.pop()
            }
        }

        override fun getHeight(textRenderer: TextRenderer): Int {
            return if (FilledMapItem.getMapState(stack, mc.world) != null) 100
            else 0
        }

        override fun getWidth(textRenderer: TextRenderer) = 72
    }
}
