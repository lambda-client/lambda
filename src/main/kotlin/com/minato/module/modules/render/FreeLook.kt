
package com.minato.module.modules.render

import com.minato.Minato.mc
import com.minato.event.events.PlayerEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.interaction.managers.rotating.Rotation
import com.minato.interaction.managers.rotating.RotationManager
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.util.extension.rotation
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
    var previousPerspective: Perspective = mc.options.perspective

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
