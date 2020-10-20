package me.zeroeightsix.kami.module.modules.render

import me.zeroeightsix.kami.event.events.RenderOverlayEvent
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.setting.Settings
import me.zeroeightsix.kami.util.color.ColorConverter
import me.zeroeightsix.kami.util.color.ColorHolder
import me.zeroeightsix.kami.util.event.listener
import me.zeroeightsix.kami.util.graphics.font.FontRenderAdapter
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.init.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.util.NonNullList
import net.minecraft.util.math.BlockPos
import net.minecraft.world.GameType
import kotlin.math.floor

@Module.Info(
        name = "ArmorHUD",
        category = Module.Category.RENDER,
        description = "Displays your armor and it's durability on screen",
        showOnArray = Module.ShowOnArray.OFF
)
object ArmorHUD : Module() {
    private val damage = register(Settings.b("Damage", false))

    init {
        listener<RenderOverlayEvent> {
            if (mc.player.isCreative || mc.player.isSpectator) return@listener
            val resolution = ScaledResolution(mc)
            val width = resolution.scaledWidth / 2 + 92
            val height = resolution.scaledHeight - 55 - if (isEyeInWater()) 10 else 0

            for ((index, itemStack) in mc.player.inventory.armorInventory.withIndex()) {
                if (itemStack.isEmpty()) continue
                val x = width - (index + 1) * 20

                GlStateManager.enableDepth()
                GlStateManager.enableTexture2D()
                RenderHelper.enableGUIStandardItemLighting()
                mc.renderItem.zLevel = 200f
                mc.renderItem.renderItemAndEffectIntoGUI(itemStack, x, height)
                mc.renderItem.renderItemOverlayIntoGUI(mc.fontRenderer, itemStack, x, height, "")
                mc.renderItem.zLevel = 0f
                RenderHelper.disableStandardItemLighting()

                if (damage.value) {
                    val dura = (itemStack.maxDamage - itemStack.itemDamage) / itemStack.maxDamage.toFloat()
                    val duraText = (dura * 100.0f).toInt().toString()
                    val green = (dura * 255.0f).toInt()
                    val red = 255 - green
                    FontRenderAdapter.drawString(duraText, x + 8 - FontRenderAdapter.getStringWidth(duraText) / 2.0f, height - 11.0f, color = ColorHolder(red, green, 0))
                }
            }
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f)
            GlStateManager.enableDepth()
        }
    }

    private fun isEyeInWater(): Boolean {
        val eyePos = mc.player.getPositionEyes(1f)
        val flooredEyePos = BlockPos(floor(eyePos.x), floor(eyePos.y), floor(eyePos.z))
        val block = mc.world.getBlockState(flooredEyePos).block
        return block == Blocks.WATER || block == Blocks.FLOWING_WATER
    }
}