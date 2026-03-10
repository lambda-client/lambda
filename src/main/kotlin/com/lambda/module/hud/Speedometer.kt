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

package com.lambda.module.hud

import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.module.HudModule
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.SpeedUnit
import net.minecraft.util.math.Vec3d

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
        listen<TickEvent.Post> {
            previousPos = currentPos
            currentPos = player.pos

            var vecDelta = player.pos.subtract(previousPos)
            if (onlyHorizontal) {
                vecDelta = Vec3d(vecDelta.x, 0.0, vecDelta.z)
            }
            speed = speedUnit.convertFromMinecraft(vecDelta.length())
        }

        onEnable {
            previousPos = player.pos
            currentPos = player.pos
            speed = 0.0
        }

        onDisable { speed = 0.0 }
    }

    override fun ImGuiBuilder.buildLayout() {
        runSafe {
            text("Speed: %.2f %s".format(speed, speedUnit.unitName))
        }
    }
}