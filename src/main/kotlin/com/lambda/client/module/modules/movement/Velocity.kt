package com.lambda.client.module.modules.movement

import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.events.PushOutOfBlocksEvent
import com.lambda.client.mixin.extension.*
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.safeListener
import com.lambda.mixin.entity.MixinEntity
import com.lambda.mixin.world.MixinBlockLiquid
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.entity.Entity
import net.minecraft.network.play.server.SPacketEntityVelocity
import net.minecraft.network.play.server.SPacketExplosion
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.sqrt

/**
 * @see MixinEntity.applyEntityCollisionHead
 * @see MixinBlockLiquid.modifyAcceleration
 */
object Velocity : Module(
    name = "Velocity",
    description = "Modify player knockback by altering velocity",
    category = Category.MOVEMENT,
    alias = arrayOf("Knockback", "AntiKnockBack", "NoPush")
) {
    private val horizontal by setting("Horizontal", 0f, -5f..5f, 0.05f)
    private val vertical by setting("Vertical", 0f, -5f..5f, 0.05f)
    private val noPush by setting("No Push", true)
    private val entity by setting("Entity", true, { noPush })
    private val liquid by setting("Liquid", true, { noPush })
    private val block by setting("Block", false, { noPush })

    init {
        safeListener<PacketEvent.Receive> {
            if (it.packet is SPacketEntityVelocity) {
                with(it.packet) {
                    if (entityID != player.entityId) return@safeListener
                    if (isZero) {
                        it.cancel()
                    } else {
                        entityVelocityMotionX = (entityVelocityMotionX * horizontal).toInt()
                        entityVelocityMotionY = (entityVelocityMotionY * vertical).toInt()
                        entityVelocityMotionZ = (entityVelocityMotionZ * horizontal).toInt()
                    }
                }
            } else if (it.packet is SPacketExplosion) {
                with(it.packet) {
                    if (isZero) {
                        it.cancel()
                    } else {
                        explosionMotionX *= horizontal
                        explosionMotionY *= vertical
                        explosionMotionZ *= horizontal
                    }
                }
            }
        }

        safeListener<PushOutOfBlocksEvent> {
            if (noPush && block) it.cancel()
        }
    }

    private val isZero get() = horizontal == 0.0f && vertical == 0.0f

    // Junky but not no compatibility issues
    @JvmStatic
    fun handleApplyEntityCollision(entity1: Entity, entity2: Entity, ci: CallbackInfo) {
        if (isDisabled || !noPush || !entity) return
        if (entity1.isRidingSameEntity(entity2) || entity1.noClip || entity2.noClip) return

        val player = mc.player ?: return
        if (entity1 != player && entity2 != player) return

        var x = entity2.posX - entity1.posX
        var z = entity2.posZ - entity1.posZ
        var dist = max(x.absoluteValue, z.absoluteValue)

        if (dist < 0.01) return

        dist = sqrt(dist)
        x /= dist
        z /= dist

        val multiplier = (1.0 / dist).coerceAtMost(1.0)
        val collisionReduction = 1.0f - entity1.entityCollisionReduction

        x *= multiplier * 0.05 * collisionReduction
        z *= multiplier * 0.05 * collisionReduction

        entity1.addCollisionVelocity(player, -x, -z)
        entity2.addCollisionVelocity(player, x, z)

        ci.cancel()
    }

    private fun Entity.addCollisionVelocity(player: EntityPlayerSP, x: Double, z: Double) {
        if (this != player && !isBeingRidden) {
            motionX += x
            motionZ += z
            isAirBorne = true
        }
    }

    @JvmStatic
    val cancelLiquidVelocity: Boolean
        get() = isEnabled && noPush && liquid
}
