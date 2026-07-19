package com.minato.module.hud

import com.minato.gui.dsl.ImGuiBuilder
import com.minato.module.HudModule
import com.minato.module.tag.ModuleTag
import com.lambda.imgui.ImColor
import com.lambda.imgui.flag.ImDrawListFlags
import java.awt.Color

object KeystrokesHud : HudModule(
    name = "Keystrokes",
    description = "Shows WASD and mouse/space/sneak keys",
    tag = ModuleTag.HUD,
) {
    val showMouse by setting("Show Mouse Buttons", true)

    override fun ImGuiBuilder.buildLayout() {
        val mc = com.minato.Minato.mc
        val options = mc.options
        val f = options.forwardKey.isPressed
        val b = options.backKey.isPressed
        val l = options.leftKey.isPressed
        val r = options.rightKey.isPressed
        val jump = options.jumpKey.isPressed
        val sneak = options.sneakKey.isPressed
        val attack = options.attackKey.isPressed
        val use = options.useKey.isPressed
        val width = 180f
        val height = 64f

        // Theme-aware colors
        val theme = effectiveTheme
        val useThemeCol = useThemeColors.value
        val useThemeBg = useThemeBackground.value
        val keyActiveBg = if (useThemeCol) Color(40, 200, 40, 200) else Color(40, 160, 40, 200)
        val keyInactiveBg = if (useThemeCol) Color(60, 60, 60, 180) else Color(40, 40, 40, 160)
        val keyBorder = if (useThemeCol) theme.borderColor else Color(0, 0, 0, 120)
        val keyTextColor = if (useThemeCol) theme.primaryTextColor else Color(230, 230, 230)
        val bg = if (useThemeBg) theme.backgroundColor else backgroundColor.value
        val fallbackBorder = if (useThemeBg) theme.borderColor else Color(0, 0, 0, 60)

        hudBackground(width, height, bg, fallbackBorder) {
            val baseX = windowPos.x + cursorPosX + 8f
            var baseY = windowPos.y + cursorPosY + 6f

            fun drawKey(x: Float, y: Float, w: Float, h: Float, label: String, on: Boolean) {
                val bgCol = if (on) keyActiveBg else keyInactiveBg
                val borderIm = ImColor.rgba(keyBorder.red, keyBorder.green, keyBorder.blue, keyBorder.alpha)
                val bgIm = ImColor.rgba(bgCol.red, bgCol.green, bgCol.blue, bgCol.alpha)
                val textIm = ImColor.rgba(keyTextColor.red, keyTextColor.green, keyTextColor.blue, keyTextColor.alpha)
                windowDrawList.addRectFilled(x, y, x + w, y + h, bgIm, style.frameRounding)
                windowDrawList.addRect(x, y, x + w, y + h, borderIm, style.frameRounding, ImDrawListFlags.None, style.frameBorderSize)
                // center text
                val tx = x + w * 0.5f - (label.length * 4f)
                val ty = y + h * 0.25f
                windowDrawList.addText(tx, ty, textIm, label)
            }

            // WASD layout
            val keyW = 36f
            val keyH = 22f
            val spacing = 6f

            // W on top
            drawKey(baseX + keyW + spacing, baseY, keyW, keyH, "W", f)
            baseY += keyH + spacing
            // A S D row
            drawKey(baseX, baseY, keyW, keyH, "A", l)
            drawKey(baseX + keyW + spacing, baseY, keyW, keyH, "S", b)
            drawKey(baseX + (keyW + spacing) * 2, baseY, keyW, keyH, "D", r)

            // actions to the right
            val actX = baseX + (keyW + spacing) * 3 + 8f
            val actY = windowPos.y + cursorPosY + 6f
            drawKey(actX, actY, keyW + 10f, keyH, "Space", jump)
            drawKey(actX, actY + keyH + spacing, keyW + 10f, keyH, "Sneak", sneak)

            if (showMouse) {
                drawKey(actX + keyW + 16f, actY, keyW, keyH, "LMB", attack)
                drawKey(actX + keyW + 16f, actY + keyH + spacing, keyW, keyH, "RMB", use)
            }

            // register RenderBuilder renderer for smoother output
            com.minato.graphics.hud.HudRenderRegistry.update(name, windowPos.x, windowPos.y, windowSize.x, windowSize.y) {
                val sw = com.minato.Minato.mc.window?.scaledWidth?.toFloat() ?: 1920f
                val sh = com.minato.Minato.mc.window?.scaledHeight?.toFloat() ?: 1080f
                // WASD positions
                val basePx = baseX
                var by = windowPos.y + 6f
                // Theme-aware RenderBuilder colors
                val rbActiveBg = if (useThemeCol) Color(40, 200, 40, 200) else Color(40, 160, 40, 200)
                val rbInactiveBg = if (useThemeCol) Color(60, 60, 60, 180) else Color(40, 40, 40, 160)
                val rbTextColor = if (useThemeCol) theme.primaryTextColor else Color(230, 230, 230)

                fun drawKeyRB(px: Float, py: Float, w: Float, h: Float, label: String, on: Boolean) {
                    val nx = px / sw
                    val ny = py / sh
                    val nw = w / sh
                    val nh = h / sh
                    val bgCol = if (on) rbActiveBg else rbInactiveBg
                    screenRect(nx, ny, nw, nh, bgCol)
                    // label — use theme-aware text color
                    screenText(label, (px + w * 0.5f) / sw, (py + h * 0.25f) / sh, 12f / sh)
                }

                drawKeyRB(basePx + keyW + spacing, windowPos.y + 6f, keyW, keyH, "W", f)
                drawKeyRB(basePx, windowPos.y + 6f + keyH + spacing, keyW, keyH, "A", l)
                drawKeyRB(basePx + keyW + spacing, windowPos.y + 6f + keyH + spacing, keyW, keyH, "S", b)
                drawKeyRB(basePx + (keyW + spacing) * 2, windowPos.y + 6f + keyH + spacing, keyW, keyH, "D", r)

                val actX = basePx + (keyW + spacing) * 3 + 8f
                drawKeyRB(actX, windowPos.y + 6f, keyW + 10f, keyH, "Space", jump)
                drawKeyRB(actX, windowPos.y + 6f + keyH + spacing, keyW + 10f, keyH, "Sneak", sneak)
                if (showMouse) {
                    drawKeyRB(actX + keyW + 16f, windowPos.y + 6f, keyW, keyH, "LMB", attack)
                    drawKeyRB(actX + keyW + 16f, windowPos.y + 6f + keyH + spacing, keyW, keyH, "RMB", use)
                }
            }

            cursorPosY += height
        }
    }
}
