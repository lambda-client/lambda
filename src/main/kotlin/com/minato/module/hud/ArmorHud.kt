package com.minato.module.hud

import com.minato.gui.dsl.ImGuiBuilder
import com.lambda.imgui.ImColor
import com.minato.module.HudModule
import com.minato.module.tag.ModuleTag
import com.minato.util.player.SlotUtils
import net.minecraft.item.ItemStack
import java.awt.Color

object ArmorHud : HudModule(
    name = "ArmorHud",
    description = "Displays equipped armor and durability",
    tag = ModuleTag.HUD,
) {
    val showDurability by setting("Show Durability", true)

    override fun ImGuiBuilder.buildLayout() {
        val player = com.minato.Minato.mc.player ?: return
        val armorSlots = player.armorSlots
        val visibleSlots = armorSlots.filter { !it.stack.isEmpty }
        val slotCount = visibleSlots.size
        val width = 160f
        val height = maxOf(20f, frameHeightWithSpacing * slotCount + style.framePadding.y * 2)

        hudBackground(width, height, backgroundColor.value, Color(0, 0, 0, 60)) {
            // draw using ImGui drawlist for live preview
            visibleSlots.forEachIndexed { idx, slot ->
                val stack = slot.stack
                val lineX = windowPos.x + cursorPosX
                val lineY = windowPos.y + cursorPosY + idx * frameHeightWithSpacing

                val name = stack.name.string
                windowDrawList.addText(lineX + 6f, lineY + 2f, ImColor.rgba(220, 220, 220, 255), name)
            }

            // register renderer for RenderBuilder (high-performance)
            com.minato.graphics.hud.HudRenderRegistry.update(name, windowPos.x, windowPos.y, windowSize.x, windowSize.y) {
                val player = com.minato.Minato.mc.player ?: return@update
                val slots = player.armorSlots.filter { !it.stack.isEmpty }
                val sw = com.minato.Minato.mc.window?.scaledWidth?.toFloat() ?: 1920f
                val sh = com.minato.Minato.mc.window?.scaledHeight?.toFloat() ?: 1080f

                slots.forEachIndexed { idx, slot ->
                    val stack = slot.stack
                    val px = windowPos.x + 6f + 8f * idx
                    val py = windowPos.y + 6f + idx * frameHeightWithSpacing
                    val normX = px / sw
                    val normY = py / sh
                    val sizePx = 24f
                    val sizeNorm = sizePx / sh
                    // draw item icon
                    screenGuiItem(stack, normX, normY, sizeNorm, centered = false)

                    if (showDurability && stack.isDamageable) {
                        val max = stack.maxDamage
                        val rem = max - stack.damage
                        val pct = rem.toDouble() / max.toDouble()
                        val barPxW = 80f
                        val barPxH = 6f
                        val bx = px + 28f
                        val by = py + sizePx * 0.5f
                        val bxN = bx / sw
                        val byN = by / sh
                        // background
                        screenRect(bxN, byN, barPxW / sw, barPxH / sh, java.awt.Color(0, 0, 0, 140))
                        // fill
                        val fillColor = when {
                            pct >= 0.75 -> java.awt.Color(0, 200, 0)
                            pct >= 0.4 -> java.awt.Color(240, 200, 0)
                            else -> java.awt.Color(220, 40, 40)
                        }
                        screenRect(bxN, byN, (barPxW * pct).toFloat() / sw, barPxH / sh, fillColor)
                    }
                }
            }

            // consume vertical space
            cursorPosY += height
        }
    }
}
