package com.lambda.client.module.modules.render

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.safeListener
import net.minecraft.entity.Entity
import net.minecraft.util.math.MathHelper
import net.minecraftforge.client.event.EntityViewRenderEvent
import net.minecraftforge.client.event.InputUpdateEvent
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

object FreeLook : Module(
    name = "FreeLook",
    description = "Look Freely",
    category = Category.RENDER
) {
    private val arrowKeyYawAdjust by setting("Arrow Key Yaw Adjust", false)
    private val arrowKeyYawAdjustIncrement by setting("Yaw Adjust Increment", 1.0f, 0.001f..10.0f, 0.001f,
        visibility = { arrowKeyYawAdjust });

    private var cameraYaw: Float = 0f
    private var cameraPitch: Float = 0f
    private var thirdPersonBefore: Int = 0

    init {
        onEnable {
            runSafe {
                thirdPersonBefore = mc.gameSettings.thirdPersonView
                mc.gameSettings.thirdPersonView = 1;
                cameraYaw = player.rotationYaw + 180.0f
                cameraPitch = player.rotationPitch
            }
        }

        onDisable {
            mc.gameSettings.thirdPersonView = thirdPersonBefore
        }

        safeListener<EntityViewRenderEvent.CameraSetup> {
            if (mc.gameSettings.thirdPersonView <= 0) return@safeListener

            it.yaw = cameraYaw
            it.pitch = cameraPitch
        }

        safeListener<InputUpdateEvent> {
            if (!arrowKeyYawAdjust) return@safeListener

            if (it.movementInput.leftKeyDown) {
                // shift cam and player rot left by x degrees
                updateYaw(-arrowKeyYawAdjustIncrement)
                it.movementInput.leftKeyDown = false
            }
            if (it.movementInput.rightKeyDown) {
                // shift cam and player rot right by x degrees
                updateYaw(arrowKeyYawAdjustIncrement)
                it.movementInput.rightKeyDown = false
            }
            it.movementInput.moveStrafe = 0.0f
        }
    }

    private fun SafeClientEvent.updateYaw(dYaw: Float) {
        cameraYaw += dYaw
        player.rotationYaw += dYaw
    }

    @JvmStatic
    fun handleTurn(entity: Entity, yaw: Float, pitch: Float, ci: CallbackInfo): Boolean {
        if (isDisabled || mc.player == null) return false
        return if (entity == mc.player) {
            this.cameraYaw += yaw * 0.15f
            this.cameraPitch -= pitch * 0.15f
            this.cameraPitch = MathHelper.clamp(this.cameraPitch, -180.0f, 180.0f)
            ci.cancel()
            true
        } else {
            false
        }
    }
}