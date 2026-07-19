package com.minato.module.hud

import com.minato.gui.dsl.ImGuiBuilder
import com.lambda.imgui.ImColor
import com.minato.module.HudModule
import com.minato.module.tag.ModuleTag
import com.minato.util.player.SlotUtils.armorSlots
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

        // Theme-aware colors
        val theme = effectiveTheme
        val useThemeCol = useThemeColors.value
        val useThemeBg = useThemeBackground.value
        val textColor = if (useThemeCol) theme.primaryTextColor else Color(220, 220, 220)
        val textColorIm = com.lambda.imgui.ImColor.rgba(textColor.red, textColor.green, textColor.blue, textColor.alpha)

        // Theme-aware background
        val bg = if (useThemeBg) theme.backgroundColor else backgroundColor.value
        val fallbackBorder = if (useThemeBg) theme.borderColor else Color(0, 0, 0, 60)

        hudBackground(width, height, bg, fallbackBorder) {
            // draw using ImGui drawlist for live preview
            visibleSlots.forEachIndexed { idx, slot ->
                val stack = slot.stack
                val lineX = windowPos.x + cursorPosX
                val lineY = windowPos.y + cursorPosY + idx * frameHeightWithSpacing

                val name = stack.name.string
                windowDrawList.addText(lineX + 6f, lineY + 2f, textColorIm, name)
            }

            // register renderer for RenderBuilder (high-performance)
            com.minato.graphics.hud.HudRenderRegistry.update(name, windowPos.x, windowPos.y, windowSize.x, windowSize.y) {
                val player = com.minato.Minato.mc.player ?: return@update
                val slots = player.armorSlots.filter { !it.stack.isEmpty }
                val sw = com.minato.Minato.mc.window?.scaledWidth?.toFloat() ?: 1920f
                val sh = com.minato.Minato.mc.window?.scaledHeight?.toFloat() ?: 1080f

                // RenderBuilder text color from theme
                val renderTextColor = if (useThemeCol) theme.primaryTextColor else Color(220, 220, 220)

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
                        screenRect(bxN, byN, barPxW / sw, barPxH / sh, Color(0, 0, 0, 140))
                        // fill — use theme-aware health bar colors
                        val fillColor = theme.healthBarColor(pct.toFloat())
                        screenRect(bxN, byN, (barPxW * pct).toFloat() / sw, barPxH / sh, fillColor)
                    }
                }
            }

            // consume vertical space
            cursorPosY += height
        }
    }
}
