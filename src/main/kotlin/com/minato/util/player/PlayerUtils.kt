
package com.minato.util.player

import com.minato.config.blocks.BuildConfig
import com.minato.context.SafeContext
import com.minato.interaction.handlers.GlideHandler
import com.minato.util.extension.getBlockState
import com.minato.util.player.MovementUtils.sneaking
import com.minato.util.world.fastEntitySearch
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.projectile.FireworkRocketEntity
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket
import net.minecraft.util.Hand
import net.minecraft.world.GameMode

object PlayerUtils {
    const val FAKE_PLAYER_ID = -2024-4-20

    val SafeContext.gamemode: GameMode
        get() = interaction.currentGameMode

    context(safeContext: SafeContext)
    val ClientPlayerEntity.hasFirework: Boolean
        get() = safeContext.fastEntitySearch<FireworkRocketEntity>(4.0) { it.shooter == this }.any()

    val ClientPlayerEntity.canStartGliding: Boolean
        get() = !isGliding && !isClimbing && !isTouchingWater && canGlide()

    context(_: SafeContext)
    val ClientPlayerEntity.canTakeoff: Boolean
        get() = (isOnGround || !isGliding) &&
                !abilities.flying &&
                !isClimbing &&
                !isTouchingWater &&
                !hasVehicle() &&
                !hasStatusEffect(StatusEffects.LEVITATION) &&
                GlideHandler.canGlide()

    fun SafeContext.copyPlayer(entity: ClientPlayerEntity) =
        ClientPlayerEntity(mc, world, mc.networkHandler, null, null, entity.lastPlayerInput, entity.isSprinting).apply {
            setPos(entity.x, entity.y, entity.z)
            setExperience(entity.experienceProgress, entity.totalExperience, entity.experienceLevel)
            health = entity.health
            absorptionAmount = entity.absorptionAmount
            pitch = entity.pitch
            yaw = entity.yaw
            headYaw = entity.headYaw
            bodyYaw = entity.bodyYaw
            velocity = entity.velocity
            movementSpeed = entity.movementSpeed
            input.sneaking = entity.isSneaking
            isSprinting = entity.isSprinting
            isSwimming = entity.isSwimming
            isOnGround = entity.isOnGround
        }

    fun SafeContext.swingHand(swingType: BuildConfig.SwingType, hand: Hand) =
        when (swingType) {
            BuildConfig.SwingType.Vanilla -> {
                swingHandClient(hand)
                connection.sendPacket(HandSwingC2SPacket(hand))
            }
            BuildConfig.SwingType.Server -> connection.sendPacket(HandSwingC2SPacket(hand))
            BuildConfig.SwingType.Client -> swingHandClient(hand)
        }

    fun SafeContext.swingHandClient(hand: Hand) {
        if (!player.handSwinging || player.handSwingTicks >= player.handSwingDuration / 2 || player.handSwingTicks < 0) {
            player.handSwingTicks = -1
            player.handSwinging = true
            player.preferredHand = hand
        }
    }

    fun SafeContext.isItemOnCooldown(stack: ItemStack) = player.itemCooldownManager.isCoolingDown(stack)

    fun ClientPlayerEntity.canGlideWithChestPiece() =
        LivingEntity.canGlideWith(getEquippedStack(EquipmentSlot.CHEST), EquipmentSlot.CHEST)

    fun SafeContext.isIn2b2tQueue(): Boolean {
        if (player.gameMode != GameMode.SPECTATOR) return false

        if (connection.listedPlayerListEntries.any { it.profile.id != player.uuid }) return false

        return (0 until world.height).all { world.getBlockState(player.blockPos.x, world.bottomY + it, player.blockPos.z).isAir }
    }
}
