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
import com.lambda.event.events.PlayerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.interaction.managers.rotating.RotationManager
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.extension.rotation
import net.minecraft.client.option.Perspective


object FreeLook : Module(
    name = "FreeLook",
    description = "Allows you to look around freely while moving",
    tag = ModuleTag.PLAYER,
    autoDisable = true
) {
    @JvmStatic val enableYaw by setting("Enable Yaw", false, "Don't effect pitch if enabled")
    @JvmStatic val enablePitch by setting("Enable Pitch", false, "Don't effect yaw if enabled")
    val togglePerspective by setting("Toggle Perspective", true, "Toggle perspective when enabling FreeLook")

    var camera: Rotation = Rotation.ZERO
    var previousPerspective: Perspective = Perspective.FIRST_PERSON

    /**
     * @see net.minecraft.entity.Entity.changeLookDirection
     */
    private const val SENSITIVITY_FACTOR = 0.15

    @JvmStatic
    fun updateCam() {
        mc.gameRenderer.apply {
            camera.setRotation(this@FreeLook.camera.yawF, this@FreeLook.camera.pitchF)
        }
    }

    init {
        previousPerspective = mc.options.perspective

        onEnable {
            camera = player.rotation
            previousPerspective = mc.options.perspective
            if (togglePerspective) mc.options.perspective = Perspective.THIRD_PERSON_BACK
        }

        onDisable {
            updateCam()
            mc.options.perspective = previousPerspective
        }

        listen<PlayerEvent.ChangeLookDirection> {
            if (!isEnabled) return@listen

            camera = camera.withDelta(
                it.deltaYaw * SENSITIVITY_FACTOR,
                it.deltaPitch * SENSITIVITY_FACTOR
            )

            if (enableYaw) RotationManager.setPlayerYaw(camera.yaw)
            if (enablePitch) RotationManager.setPlayerPitch(camera.pitch)

            it.cancel()
        }
    }
}
