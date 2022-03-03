package com.lambda.client.module.modules.movement

import com.lambda.client.event.events.PlayerTravelEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.MovementUtils
import com.lambda.client.util.MovementUtils.calcMoveYaw
import com.lambda.client.util.MovementUtils.isMoving
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.safeListener
import net.minecraft.init.MobEffects
import net.minecraftforge.fml.common.gameevent.TickEvent
import kotlin.math.cos
import kotlin.math.sin

object AntiLevitation : Module(
    name = "AntiLevitation",
    description = "Removes levitation potion effect (boring) or abuse it (epic)",
    category = Category.MOVEMENT
) {
    private val fly by setting("Fly", true, description = "Allows you to \"fly\" when you have levitation")
    private val vertical by setting("Only Vertical", false, { fly }, description = "doesn't apply extra speed when enabled")
    private val YMotion by setting("Constant Motion UP", 0.002f, 0.0f..0.02f, 0.001f, { fly }, description = "The Y Motion that is always applied to bypass the anticheat")
    private val speed by setting("Speed",  0.28f, 0.15f..0.3f, 0.005f, { fly }, description = "The speed you fly at")

    private var ready = false

    init {
        onDisable {
            ready = false
            if (fly) {
                runSafe {
                    player.capabilities?.apply {
                        isFlying = false
                        flySpeed = 0.05f
                    }
                }
            }
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (player.isPotionActive(MobEffects.LEVITATION)) {
                if (fly) {
                    ready = true
                } else {
                    player.removeActivePotionEffect(MobEffects.LEVITATION)
                    MessageSendHelper.sendChatMessage("Removed levitation effect.")
                }
            } else {
                if (fly && ready) {
                    runSafe {
                        player.capabilities?.apply {
                            isFlying = false
                            flySpeed = 0.05f
                        }
                    }
                    MessageSendHelper.sendChatMessage("Levitation ran out. Brace for impact....")
                    ready = false
                }
            }
        }

        safeListener<PlayerTravelEvent> {
            if (ready) {
                /* Makes the player fly and set the speed to the user's preference */
                if (MovementUtils.isInputting && !vertical) {
                    val yaw = calcMoveYaw()
                    player.motionX = -sin(yaw) * speed
                    player.motionZ = cos(yaw) * speed
                } else {
                    player.motionX = 0.0
                    player.motionY = 0.0
                    player.motionZ = 0.0
                }
                //TODO: make it not flag when crouching
                //TODO: disable sprinting
                //TODO: find optimal speed that never flags

                /* Apply Y motion the player wants to move to trick the anticheat */
                if (MovementUtils.isInputting || player.isMoving) {
                    player.motionY = YMotion.toDouble()
                }

                /* Vertical movement */
                if (mc.gameSettings.keyBindJump.isKeyDown) player.motionY = (speed / 1.1f).toDouble()
                if (mc.gameSettings.keyBindSneak.isKeyDown) player.motionY = -(speed / 0.5f).toDouble() // You can go down way faster then going up
            }
        }
    }
}