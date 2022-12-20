package com.lambda.client.module.modules.combat

import com.lambda.client.commons.interfaces.DisplayEnum
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.CriticalsUpdateWalkingEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.events.PlayerAttackEvent
import com.lambda.client.mixin.extension.isInWeb
import com.lambda.client.mixin.extension.playerIsOnGround
import com.lambda.client.mixin.extension.playerMoving
import com.lambda.client.mixin.extension.playerY
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.EntityUtils.isInOrAboveLiquid
import com.lambda.client.util.threads.safeListener
import com.lambda.mixin.accessor.player.AccessorEntityPlayerSP
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.init.MobEffects
import net.minecraft.network.play.client.CPacketAnimation
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.network.play.client.CPacketUseEntity
import net.minecraft.util.EnumHand
import net.minecraft.world.GameType
import net.minecraftforge.fml.common.gameevent.TickEvent

object Criticals : Module(
    name = "Criticals",
    description = "Always do critical attacks",
    category = Category.COMBAT
) {
    private val mode by setting("Mode", Mode.EDIT)
    private val jumpMotion by setting("Jump Motion", 0.25, 0.1..0.5, 0.01, { mode == Mode.MINI_JUMP }, fineStep = 0.001)
    private val attackFallDistance by setting("Attack Fall Distance", 0.1, 0.05..1.0, 0.05, { mode == Mode.MINI_JUMP || mode == Mode.JUMP })

    private enum class Mode(override val displayName: String) : DisplayEnum {
        PACKET("Packet"),
        EDIT("Edit"),
        JUMP("Jump"),
        MINI_JUMP("Mini Jump")
    }

    private var delayTick = -1
    private var target: Entity? = null
    private var spoofedY = -1337.0
    private var attacking = false

    override fun isActive(): Boolean {
        return isEnabled && !delaying()
    }

    override fun getHudInfo(): String {
        return mode.displayName
    }

    init {
        onDisable {
            reset()
        }

        safeListener<CriticalsUpdateWalkingEvent> {

            // we need to ensure we are always sending packets as not to flag NCP for fight.critical
            if (mode == Mode.EDIT)
                (player as AccessorEntityPlayerSP).lcSetLastReportedPosY(-1337.0)

        }

        safeListener<PacketEvent.Send> {

            if (it.packet is CPacketAnimation && mode != Mode.PACKET && delayTick > -1) {

                it.cancel()

            } else if (it.packet is CPacketPlayer && mode == Mode.EDIT) {

                // the advantage of this is that it doesn't delay anything and doesn't send extra packets
                if (player.onGround) {

                    if (spoofedY <= 0)
                        spoofedY = .01
                    else
                        spoofedY -= .00001

                } else
                    spoofedY = -1337.0

                it.packet.playerMoving = true
                it.packet.playerIsOnGround = false

                if (spoofedY >= 0)
                    it.packet.playerY += spoofedY

            }

        }

        safeListener<PlayerAttackEvent>(0) {
            if (it.cancelled || attacking || it.entity !is EntityLivingBase || !canDoCriticals(true)) return@safeListener

            val cooldownReady = player.onGround && player.getCooledAttackStrength(0.5f) > 0.9f

            when (mode) {

                Mode.PACKET -> {
                    if (cooldownReady) {
                        connection.sendPacket(CPacketPlayer.Position(player.posX, player.posY + 0.1, player.posZ, false))
                        connection.sendPacket(CPacketPlayer.Position(player.posX, player.posY, player.posZ, false))
                    }
                }

                Mode.JUMP -> {
                    jumpAndCancel(it, cooldownReady, null)
                }

                Mode.MINI_JUMP -> {
                    jumpAndCancel(it, cooldownReady, jumpMotion)
                }

                else -> {
                }

            }
        }

        safeListener<TickEvent.ClientTickEvent> { event ->
            if (event.phase != TickEvent.Phase.END || mode == Mode.PACKET || delayTick <= -1) return@safeListener

            delayTick--

            if (target != null && player.fallDistance >= attackFallDistance && canDoCriticals(!player.onGround)) {
                val target = target
                reset()

                if (target != null) {
                    attacking = true
                    connection.sendPacket(CPacketUseEntity(target))
                    connection.sendPacket(CPacketAnimation(EnumHand.MAIN_HAND))
                    attacking = false
                }
            }
        }
    }

    private fun reset() {
        delayTick = -1
        target = null
    }

    private fun SafeClientEvent.jumpAndCancel(event: PlayerAttackEvent, cooldownReady: Boolean, motion: Double?) {
        if (cooldownReady && !delaying()) {
            player.jump()
            if (motion != null) player.motionY = motion
            target = event.entity

            if (playerController.currentGameType != GameType.SPECTATOR) {
                player.attackTargetEntityWithCurrentItem(event.entity)
                player.resetCooldown()
            }

            delayTick = 20
        }

        event.cancel()
    }

    private fun delaying() =
        mode != Mode.PACKET && delayTick > -1 && target != null

    private fun SafeClientEvent.canDoCriticals(onGround: Boolean) =
        onGround
            && !player.isInWeb
            && !player.isOnLadder
            && !player.isRiding
            && !player.isPotionActive(MobEffects.BLINDNESS)
            && !player.isInOrAboveLiquid
}