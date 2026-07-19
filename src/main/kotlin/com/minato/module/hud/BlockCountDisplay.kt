package com.minato.module.hud

import com.minato.gui.dsl.ImGuiBuilder
import com.lambda.imgui.ImColor
import com.minato.module.HudModule
import com.minato.module.tag.ModuleTag
import com.minato.util.player.SlotUtils
import net.minecraft.item.BlockItem

object BlockCountDisplay : HudModule(
    name = "BlockCountDisplay",
    description = "Shows count of block items in inventory",
    tag = ModuleTag.HUD,
) {
    val showByType by setting("Show By Type", false)

    override fun ImGuiBuilder.buildLayout() {
        val player = com.minato.Minato.mc.player ?: return
        val stacks = player.hotbarAndInventoryStacks
        var total = 0
        val map = mutableMapOf<String, Int>()
        stacks.forEach { stack ->
            if (stack.isEmpty) return@forEach
            if (stack.item is BlockItem) {
                total += stack.count
                if (showByType) {
                    val key = stack.item.translationKey
                    map[key] = map.getOrDefault(key, 0) + stack.count
                }
            }
        }

        val width = 160f
        val lines = 1 + if (showByType && map.isNotEmpty()) map.size.coerceAtMost(6) else 0
        val height = maxOf(20f, frameHeightWithSpacing * lines + style.framePadding.y * 2)

        hudBackground(width, height, backgroundColor.value, Color(0, 0, 0, 60)) {
            val baseX = windowPos.x + cursorPosX + 6f
            var y = windowPos.y + cursorPosY + 4f
            windowDrawList.addText(baseX, y, ImColor.rgba(220, 220, 220, 255), "Blocks: $total")
            y += frameHeightWithSpacing
            if (showByType && map.isNotEmpty()) {
                map.entries.sortedByDescending { it.value }.take(6).forEach { (k, v) ->
                    val name = k.substringAfterLast('.')
                    windowDrawList.addText(baseX + 6f, y, ImColor.rgba(200, 200, 200, 230), "${name} : $v")
                    y += frameHeightWithSpacing
                }
            }

            com.minato.graphics.hud.HudRenderRegistry.update(name, windowPos.x, windowPos.y, windowSize.x, windowSize.y) {
                // render counts using RenderBuilder for crisp text
                val sw = com.minato.Minato.mc.window?.scaledWidth?.toFloat() ?: 1920f
                val sh = com.minato.Minato.mc.window?.scaledHeight?.toFloat() ?: 1080f
                val basePx = windowPos.x + 6f
                var py = windowPos.y + 6f
                screenText("Blocks: $total", basePx / sw, py / sh, 14f / sh)
                py += frameHeightWithSpacing
                if (showByType && map.isNotEmpty()) {
                    map.entries.sortedByDescending { it.value }.take(6).forEach { (k, v) ->
                        val name = k.substringAfterLast('.')
                        screenText("${name} : $v", (basePx + 6f) / sw, py / sh, 12f / sh)
                        py += frameHeightWithSpacing
                    }
                }
            }

            cursorPosY += height
        }
    }
}
