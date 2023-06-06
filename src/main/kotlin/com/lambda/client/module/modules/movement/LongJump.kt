package com.lambda.client.module.modules.movement

import com.lambda.client.commons.interfaces.DisplayEnum
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.events.PlayerMoveEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.MovementUtils.applySpeedPotionEffects
import com.lambda.client.util.MovementUtils.calcMoveYaw
import com.lambda.client.util.MovementUtils.isInputting
import com.lambda.client.util.threads.safeListener
import net.minecraft.network.play.server.SPacketPlayerPosLook
import net.minecraftforge.fml.common.gameevent.TickEvent
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin


/**
 * @author Doogie13
 * @since 17/03/2023
 */
object LongJump : Module(
    name = "LongJump",
    description = "Allows you to abuse velocity to jump further",
    category = Category.MOVEMENT
) {

    private val mode by setting("Mode", Mode.STANDARD, description = "How to boost your motion")
    private var speed by setting("Speed", 3.8, 0.0..10.0, 0.001, description = "How much to boost your initial motion")
    private val virtue by setting("Glide", false, description = "Glide along the ground after jumping") // extends the boost from LJ. reasonably major 2014 exploit, works on UpdatedNCP in 2023
    private val applyPots by setting("Apply Speed Pots", true, description = "Whether to apply Speed potion effect") // sometimes we don't want to due to some arbitrary top speed

    enum class Mode(override val displayName: String) : DisplayEnum {
        STANDARD("Standard"),
        UPDATED("UpdatedNCP")
    }

    private var currentSpeed = .2873
    private var strafePhase = StandardPhase.HEAD_START
    private var lastDistance = 0.0
    private var jumpTick = 0

    init {

        onEnable {
            strafePhase = StandardPhase.HEAD_START
            currentSpeed = .2873
            lastDistance = 0.0
            jumpTick = 0
        }

        safeListener<PacketEvent.Receive> {
            if (it.packet is SPacketPlayerPosLook) {
                currentSpeed = .0
                disable()
            }
        }

        safeListener<TickEvent.ClientTickEvent> {

            if (it.phase != TickEvent.Phase.START)
                return@safeListener

            lastDistance = hypot(player.posX - player.lastTickPosX, player.posZ - player.lastTickPosZ)

        }

        safeListener<PlayerMoveEvent> {

            val base = if (applyPots) applySpeedPotionEffects(.2873) else .2873
            val adjSpeed = speed * base // this seems to be what future does so its what people are expecting
            val yaw = calcMoveYaw()

            if (player.capabilities.isFlying
                || player.isElytraFlying
                || BaritoneUtils.isPathing
                || !isInputting) {
                strafePhase = StandardPhase.HEAD_START
                return@safeListener
            }

            when (mode) {

                Mode.STANDARD -> {

                    when (strafePhase) {

                        StandardPhase.HEAD_START -> {
                            if (!player.onGround) {
                                strafePhase = StandardPhase.SLOWDOWN
                                return@safeListener
                            }
                            currentSpeed = adjSpeed / 2.149
                            strafePhase = StandardPhase.ACCELERATING
                        }

                        StandardPhase.ACCELERATING -> {
                            currentSpeed = adjSpeed
                            player.motionY = .424 // slightly higher than normal which is good for LJ
                            strafePhase = StandardPhase.SLOWDOWN
                        }

                        StandardPhase.SLOWDOWN -> {
                            currentSpeed -= .66 * base
                            strafePhase = StandardPhase.FALLING
                        }

                        StandardPhase.FALLING -> {

                            if (player.onGround) {
                                strafePhase = StandardPhase.ACCELERATING
                                return@safeListener
                            }

                            currentSpeed = lastDistance - lastDistance / 159
                        }

                    }

                    it.x = -sin(yaw) * currentSpeed
                    it.z = cos(yaw) * currentSpeed

                    if (
                        virtue
                        && isInputting
                        && world.collidesWithAnyBlock(player.entityBoundingBox.shrink(0.0625).expand(0.0, -0.55, 0.0))
                        && player.motionY < 0
                        && currentSpeed > .29
                        && strafePhase == StandardPhase.FALLING
                    ) {
                        it.y = -1e-7
                    }

                }

                Mode.UPDATED -> {

                    // we cannot spam this on UpdatedNCP
                    if (jumpTick > 1 && player.onGround) {
                        disable()
                        return@safeListener
                    }

                    if (player.onGround) {
                        player.jump()
                        it.x = -sin(yaw) * base
                        it.z = cos(yaw) * base
                        jumpTick = 1
                        return@safeListener
                    }

                    if (++jumpTick == 2) {
                        it.x = -sin(yaw) * adjSpeed
                        it.z = cos(yaw) * adjSpeed
                        return@safeListener
                    }

                    if (jumpTick < 8) {
                        val newSpeed = lastDistance - lastDistance / 159
                        it.x = -sin(yaw) * newSpeed
                        it.z = cos(yaw) * newSpeed
                    }

                    if (
                        virtue
                        && isInputting
                        && world.collidesWithAnyBlock(player.entityBoundingBox.shrink(0.0625).expand(0.0, -0.55, 0.0))
                        && player.motionY < 0
                        && currentSpeed > .29
                    ) {
                        it.y = -1e-7
                    }

                }

            }

        }

    }

    private enum class StandardPhase {
        // slide along the ground slower to bypass
        HEAD_START,

        // to jump and accelerate
        ACCELERATING,

        // to fall to the ground
        SLOWDOWN,

        // to slowly fall to the ground
        FALLING
    }

}