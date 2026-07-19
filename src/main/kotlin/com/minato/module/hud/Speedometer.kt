
package com.minato.module.hud

import com.minato.context.SafeContext
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.gui.dsl.ImGuiBuilder
import com.minato.module.HudModule
import com.minato.module.tag.ModuleTag
import com.minato.util.SpeedUnit
import net.minecraft.util.math.Vec3d
import java.awt.Color

object Speedometer : HudModule(
    name = "Speedometer",
    description = "Displays player speed",
    tag = ModuleTag.HUD
) {
    var speedUnit by setting("Speed Unit", SpeedUnit.MetersPerSecond)
    var onlyHorizontal by setting("Horizontal Speed", false, description = "Only consider horizontal movement for speed calculation")

    var previousPos: Vec3d = Vec3d.ZERO
    var currentPos: Vec3d = Vec3d.ZERO
    var speed: Double = 0.0

    init {
        listen<TickEvent.Post>(alwaysListen = true) {
            previousPos = currentPos
            currentPos = player.pos
            speed = calculateSpeed()
        }

        onEnable {
            previousPos = player.pos
            currentPos = player.pos
            speed = 0.0
        }

        onDisable { speed = 0.0 }
    }

    context(safeContext: SafeContext)
    fun calculateSpeed(
        onlyHorizontal: Boolean = this.onlyHorizontal,
        speedUnit: SpeedUnit = this.speedUnit
    ) = with(safeContext) {
        var vecDelta = player.pos.subtract(previousPos)
        if (onlyHorizontal) {
            vecDelta = Vec3d(vecDelta.x, 0.0, vecDelta.z)
        }
        speedUnit.convertFromMinecraft(vecDelta.length())
    }

    override fun ImGuiBuilder.buildLayout() {
        val theme = effectiveTheme
        val useThemeCol = useThemeColors.value
        val useThemeBg = useThemeBackground.value
        val textColor = if (useThemeCol) theme.primaryTextColor else Color(220, 220, 220)
        val bg = if (useThemeBg) theme.backgroundColor else backgroundColor.value
        val fallbackBorder = if (useThemeBg) theme.borderColor else Color(0, 0, 0, 60)

        val width = 180f
        val height = frameHeightWithSpacing + style.framePadding.y * 2

        hudBackground(width, height, bg, fallbackBorder) {
            textColored("Speed: %.2f %s".format(speed, speedUnit.unitName), textColor)
            cursorPosY += height
        }
    }
}