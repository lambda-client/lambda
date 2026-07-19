
package com.minato.module.modules.render

import com.minato.Minato.mc
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.tooltip.TooltipComponent
import net.minecraft.client.render.MapRenderState
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
    tag = ModuleTag.RENDER,
) {
    @JvmStatic val showInSlot by setting("Show In Slot", true, "Shows the map in the slot rather than the basic map icon")

    private val background = Identifier.ofVanilla("textures/map/map_background.png")

    class MapComponent(val stack: ItemStack) : TooltipData, TooltipComponent {
        val state: MapState?
            get() = FilledMapItem.getMapState(stack, mc.world)

        val mapId: MapIdComponent?
            get() = stack.getOrDefault(DataComponentTypes.MAP_ID, null)

        override fun drawItems(textRenderer: TextRenderer, x: Int, y: Int, width: Int, height: Int, context: DrawContext) {
            // All credits go to this guy
            // https://github.com/VendoAU/MapTooltip/blob/2453102cd5cdf0d90452ddcd8725446922a4e948/common/src/main/java/com/vendoau/maptooltip/MapTooltipComponent.java

            val id = mapId ?: return
            val state = state ?: return
            val matrices = context.matrices

            // Render the map background
            matrices.pushMatrix()
            context.drawTexture(RenderPipelines.GUI_TEXTURED, background, x, y, 0f, 0f, 64, 64, 64, 64, 64, 64)
            matrices.popMatrix()

            // Render the map texture
            matrices.pushMatrix()
            matrices.translate(x + 3.2f, y + 3.2f)
            matrices.scale(0.45f, 0.45f)

            val renderState = MapRenderState()
            mc.mapRenderer.update(id, state, renderState)
            context.drawMap(renderState)
            matrices.popMatrix()
        }

        override fun getHeight(textRenderer: TextRenderer) =
            if (state != null) 66 else 0

        override fun getWidth(textRenderer: TextRenderer) = 66
    }
}
