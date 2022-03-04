package com.lambda.client.module.modules.movement

import com.lambda.client.event.events.PlayerTravelEvent
import com.lambda.client.manager.managers.TimerManager.modifyTimer
import com.lambda.client.manager.managers.TimerManager.resetTimer
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.MovementUtils
import com.lambda.client.util.MovementUtils.calcMoveYaw
import com.lambda.client.util.MovementUtils.isMoving
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
    //TODO: Make it a mode instead of an option + add a legit mode
    private val fly by setting("Fly", true, description = "Allows you to \"fly\" when you have levitation")
    private val vertical by setting("Only Vertical", false, { fly }, description = "doesn't apply extra speed when enabled")
    private val YMotion by setting("Motion UP", 0.002f, 0.0f..0.02f, 0.001f, { fly }, description = "The Y Motion that is applied when moving to bypass the anticheat")
    private val speed by setting("Speed",  0.28f, 0.15f..0.3f, 0.005f, { !vertical && fly }, description = "The speed you fly at")
    private val timer by setting("Timer Boost",  true, { !vertical && fly }, description = "Use timer for a slight speed boost")
    private val timerSpeed by setting("Timer Speed",  1.15f, 1.1f..1.2f, 0.01f, { timer && !vertical && fly }, description = "The timer modifier")

    private var ready = false

    init {
        onDisable {
            ready = false
            resetTimer()
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (player.isPotionActive(MobEffects.LEVITATION)) {
                if (fly) {
                    if (!ready) MessageSendHelper.sendChatMessage("You can now fly.")
                    ready = true
                } else {
                    player.removeActivePotionEffect(MobEffects.LEVITATION)
                    MessageSendHelper.sendChatMessage("Removed levitation effect.")
                }
            } else {
                if (fly && ready) {
                    MessageSendHelper.sendChatMessage("Levitation ran out. Brace for impact....")
                    ready = false
                }
            }
        }

        safeListener<PlayerTravelEvent> {
            if (ready) {
                if (MovementUtils.isInputting && !vertical) {
                    /* Makes the player move when they press a movement key */
                    val yaw = calcMoveYaw()
                    player.isSprinting = false //disables sprinting so you can't go too fast
                    player.motionX = -sin(yaw) * speed
                    player.motionZ = cos(yaw) * speed
                    if (timer && !vertical) modifyTimer(50.0f / timerSpeed)
                } else {
                    player.motionY = 0.0 // Makes the Y motion always be 0.0 when not pressing any key so levitation doesn't work

                    /* Make the motion slowly become 0 so it flattens out smooth */
                    player.motionX *= 0.85 // Possible memory leak? cuz it keeps making the number smaller i guess
                    player.motionZ *= 0.85
                }

                /* Apply tiny Y motion when the player wants to move to trick the anticheat */
                if (MovementUtils.isInputting || player.isMoving) {
                    player.motionY = YMotion.toDouble()
                }

                /* Vertical movement */
                if (mc.gameSettings.keyBindJump.isKeyDown) player.motionY = 0.145
                if (mc.gameSettings.keyBindSneak.isKeyDown) player.motionY = -0.49 // You can go down way faster then going up for some reason
            }
        }
    }
}