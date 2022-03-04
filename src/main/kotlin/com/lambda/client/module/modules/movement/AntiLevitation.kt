package com.lambda.client.module.modules.movement

import com.lambda.client.event.events.PlayerTravelEvent
import com.lambda.client.manager.managers.TimerManager.modifyTimer
import com.lambda.client.manager.managers.TimerManager.resetTimer
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.MovementUtils
import com.lambda.client.util.MovementUtils.calcMoveYaw
import com.lambda.client.util.MovementUtils.isMoving
import com.lambda.client.util.MovementUtils.setSpeed
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.init.MobEffects
import net.minecraftforge.fml.common.gameevent.TickEvent
import kotlin.math.cos
import kotlin.math.sin

object AntiLevitation : Module(
    name = "AntiLevitation",
    description = "Removes levitation effect (boring) or abuse it (epic)",
    category = Category.MOVEMENT
) {
    private val mode by setting("Mode", Mode.LEGIT, description = "The AntiLevitation mode")

    /* Flight mode */
    private val vertical by setting("Only Vertical", false, { mode == Mode.FLIGHT }, description = "doesn't apply extra speed when enabled")
    private val YMotion by setting("Y Motion", 0.002f, 0.0f..0.02f, 0.001f, { mode == Mode.FLIGHT }, description = "The Y Motion that is applied when moving to bypass the anticheat")
    private val speed by setting("Speed",  0.28f, 0.15f..0.3f, 0.005f, { !vertical && mode == Mode.FLIGHT }, description = "The speed you fly at")
    private val timer by setting("Timer Boost",  true, { !vertical && mode == Mode.FLIGHT }, description = "Use timer for a slight speed boost")
    private val timerSpeed by setting("Timer Speed",  1.15f, 1.1f..1.2f, 0.01f, { timer && !vertical && mode == Mode.FLIGHT }, description = "The timer modifier")

    /* Legit mode */
    private val LegitYMotion by setting("Motion UP", 0.018f, 0.001f..0.1f, 0.001f, { mode == Mode.LEGIT }, description = "The Y Motion that is applied when moving to bypass the anticheat")
    private val boost by setting("Sprint Boost", true, { mode == Mode.LEGIT }, description = "Gives you extra motion when control is pressed")

    /* Jump motion (used by flight mode and legit mode) */
    private val jumpMotion by setting("Jump Motion", 0.099f, 0.090f..0.10f, 0.001f, { mode == Mode.FLIGHT || mode == Mode.LEGIT}, description = "The Y Motion that is applied when you press space")

    private var ready = false
    private var legitReady = false

    private enum class Mode {
        REMOVE, FLIGHT, LEGIT
    }

    init {
        onDisable {
            ready = false
            resetTimer()
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (player.isPotionActive(MobEffects.LEVITATION)) {
                if (mode != Mode.REMOVE && !ready) {
                    ready = true
                    MessageSendHelper.sendChatMessage("You can now fly.")
                }
                if (mode == Mode.REMOVE) {
                    player.removeActivePotionEffect(MobEffects.LEVITATION)
                    MessageSendHelper.sendChatMessage("Removed levitation effect.")
                }
            } else {
                if (ready) {
                    resetTimer()
                    MessageSendHelper.sendWarningMessage("Levitation ran out. Brace for impact....")
                    mc.player.setVelocity(0.0,0.0,0.0)
                    ready = false
                }
            }
        }

        safeListener<PlayerTravelEvent> {
            if (ready) {
                if (mode == Mode.FLIGHT) {
                    if (MovementUtils.isInputting && !vertical) {
                        player.isSprinting = false //disables sprinting so you can't go too fast
                        setSpeed(speed.toDouble())
                        if (timer && !vertical) modifyTimer(50.0f / timerSpeed)
                    } else {
                        player.motionY = 0.0
                        /* Make the motion slowly become 0 so it flattens out smooth */
                        player.motionX *= 0.8
                        player.motionZ *= 0.8
                    }

                    if (MovementUtils.isInputting || player.isMoving) {
                        player.motionY = YMotion.toDouble()
                    }

                    if (mc.gameSettings.keyBindJump.isKeyDown) player.motionY = jumpMotion.toDouble()
                    if (mc.gameSettings.keyBindSneak.isKeyDown) player.motionY = -0.49
                } else if (mode == Mode.LEGIT) {
                    /* Override vanilla motion with our own motion */
                    player.motionY = LegitYMotion.toDouble()

                    if (mc.gameSettings.keyBindJump.isKeyDown) player.motionY = jumpMotion.toDouble()
                    if (mc.gameSettings.keyBindSneak.isKeyDown) player.motionY = 0.005

                    if (mc.gameSettings.keyBindSprint.isKeyDown && player.isSprinting && boost) { //player must be sprinting so you can only boost when you press W
                        val yaw = calcMoveYaw()
                        player.motionX = -sin(yaw) * 0.26
                        player.motionZ = cos(yaw) * 0.26
                    }
                }
            }
        }
    }
}