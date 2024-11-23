/*
 * Copyright 2024 Lambda
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

package com.lambda.module.modules.combat

import com.lambda.config.groups.InteractionSettings
import com.lambda.config.groups.RotationSettings
import com.lambda.config.groups.Targeting
import com.lambda.context.SafeContext
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.PlayerPacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.RotationManager
import com.lambda.interaction.RotationManager.requestRotation
import com.lambda.interaction.rotation.Rotation
import com.lambda.interaction.rotation.Rotation.Companion.dist
import com.lambda.interaction.rotation.Rotation.Companion.rotationTo
import com.lambda.interaction.rotation.RotationContext
import com.lambda.interaction.visibilty.VisibilityChecker.scanVisibleSurfaces
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runConcurrent
import com.lambda.threading.runSafe
import com.lambda.util.math.MathUtils.random
import com.lambda.util.math.VecUtils.distSq
import com.lambda.util.math.VecUtils.plus
import com.lambda.util.math.VecUtils.times
import com.lambda.util.math.lerp
import com.lambda.util.player.MovementUtils.moveDiff
import com.lambda.util.player.prediction.buildPlayerPrediction
import com.lambda.util.world.raycast.RayCastUtils.entityResult
import kotlinx.coroutines.delay
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.math.Vec3d
import kotlin.math.pow

object KillAura : Module(
    name = "KillAura",
    description = "Attacks entities",
    defaultTags = setOf(ModuleTag.COMBAT, ModuleTag.RENDER)
) {
    private val page by setting("Page", Page.Interact)

    // Interact
    private val interactionSettings = InteractionSettings(this, 3.0) { page == Page.Interact }
    private val attackMode by setting("Attack Mode", AttackMode.Cooldown) { page == Page.Interact }
    private val delaySync by setting("Client-side Delay", true) { page == Page.Interact && attackMode == AttackMode.Cooldown }
    private val cooldownSync by setting("Client-side Cooldown", true) { page == Page.Interact && attackMode == AttackMode.Cooldown }
    private val timerSync by setting("Assume Timer", true) { page == Page.Interact && attackMode == AttackMode.Cooldown && delaySync }
    private val cooldownOffset by setting("Cooldown Offset", 0, -5..5, 1) { page == Page.Interact && attackMode == AttackMode.Cooldown }
    private val hitDelay1 by setting("Hit Delay 1", 2.0, 0.0..20.0, 1.0) { page == Page.Interact && attackMode == AttackMode.Delay }
    private val hitDelay2 by setting("Hit Delay 2", 6.0, 0.0..20.0, 1.0) { page == Page.Interact && attackMode == AttackMode.Delay }
    private val criticalSync by setting("Critical Sync", true) { page == Page.Interact && attackMode == AttackMode.Cooldown  }

    // Targeting
    private val targeting = Targeting.Combat(this) { page == Page.Targeting }

    // Aiming
    private val rotate by setting("Rotate", true) { page == Page.Aiming }
    private val rotation = RotationSettings(this) { page == Page.Aiming && rotate }
    private val stabilize by setting("Stabilize", true) { page == Page.Aiming && !rotation.instant && rotate }
    private val stabilizationSpeed by setting("Stabilization Speed", 1.0, 0.1..3.0, 0.01) { page == Page.Aiming && !rotation.instant && rotate && stabilize }
    private val centerFactor by setting("Center Factor", 0.4, 0.0..1.0, 0.01) { page == Page.Aiming && rotate }
    private val shakeFactor by setting("Shake Factor", 0.4, 0.0..1.0, 0.01) { page == Page.Aiming && rotate }
    private val shakeChance by setting("Shake Chance", 0.2, 0.05..1.0, 0.01) { page == Page.Aiming && shakeFactor > 0.0 && rotate }
    private val selfPredict by setting("Self Predict", 1.0, 0.0..2.0, 0.1) { page == Page.Aiming && rotate }
    private val targetPredict by setting("Target Predict", 0.0, 0.0..2.0, 0.1) { page == Page.Aiming && rotate }

    var target: LivingEntity? = null; private set

    private var shakeRandom = Vec3d.ZERO

    private var attackTicks = 0
    private var lastAttackTime = 0L
    private var hitDelay = 100.0

    private var prevY = 0.0
    private var lastY = 0.0
    private var lastOnGround = true
    private var onGroundTicks = 0

    enum class Page {
        Interact,
        Targeting,
        Aiming
    }

    enum class AttackMode {
        Cooldown,
        Delay
    }

    init {
        requestRotation(
            onUpdate = {
                if (!rotate) return@requestRotation null

                target?.let { target ->
                    buildRotation(target)
                }
            }
        )

        listener<PlayerPacketEvent.Pre>(Int.MIN_VALUE) { event ->
            prevY = lastY
            lastY = event.position.y
            lastOnGround = event.onGround
        }

        listener<TickEvent.Pre> {
            target = targeting.target()
            if (!timerSync) attackTicks++

            target?.let { entity ->
                runAttack(entity)
            }
        }

        runConcurrent {
            while (true) {
                delay(50) // ToDo: tps sync

                runSafe {
                    if (timerSync && isEnabled) attackTicks++
                }
            }
        }

        listener<PacketEvent.Send.Post> { event ->
            if (event.packet !is HandSwingC2SPacket &&
                event.packet !is UpdateSelectedSlotC2SPacket &&
                event.packet !is PlayerInteractEntityC2SPacket
            ) return@listener

            attackTicks = 0
        }

        onEnable(::reset)
        onDisable(::reset)
    }

    private fun SafeContext.buildRotation(target: LivingEntity): RotationContext? {
        val currentRotation = RotationManager.currentRotation

        val prediction = buildPlayerPrediction()

        val eye = when {
            selfPredict < 1 -> {
                lerp(selfPredict, player.eyePos, prediction.next().eyePos)
            }

            selfPredict < 2 -> {
                val pos1 = prediction.next().eyePos
                val pos2 = prediction.next().eyePos

                lerp(selfPredict - 1, pos1, pos2)
            }

            else -> {
                prediction.next().next().eyePos
            }
        }

        val box = target.boundingBox

        val reach = targeting.targetingRange + 2.0
        val reachSq = reach.pow(2)

        // Do not rotate if the eyes are inside the target's AABB
        if (box.contains(eye)) {
            return RotationContext(currentRotation, rotation)
        }

        // Rotation stabilizer
        rotation.speedMultiplier = if (stabilize && !rotation.instant) {
            val slowDown = currentRotation.castBox(box, reach, eye) != null

            with(rotation) {
                val targetSpeed = if (slowDown) 0.0 else 1.0
                val acceleration = if (slowDown) 0.2 * stabilizationSpeed else 0.1 / stabilizationSpeed

                targetSpeed.coerceIn(
                    speedMultiplier - acceleration,
                    speedMultiplier + acceleration
                )
            }
        } else 1.0

        // Update shake vector
        if (random(0.0, 1.0) < shakeChance) {
            shakeRandom = Vec3d(
                random(0.0, 1.0),
                random(0.0, 1.0),
                random(0.0, 1.0),
            )
        }

        // Find the closest point to the player's eyes
        var vec = Vec3d(
            eye.x.coerceIn(box.minX, box.maxX),
            eye.y.coerceIn(box.minY, box.maxY),
            eye.z.coerceIn(box.minZ, box.maxZ)
        )

        val random = Vec3d(
            lerp(shakeRandom.x, box.minX, box.maxX),
            lerp(shakeRandom.x, box.minY, box.maxY),
            lerp(shakeRandom.x, box.minZ, box.maxZ)
        )

        vec = lerp(centerFactor, vec, box.center) // Mix with center
        vec = lerp(shakeFactor, vec, random) // Apply shaking

        // Raycast
        run {
            if (!interactionSettings.useRayCast) return@run

            val vecRotation = eye.rotationTo(vec)
            if (vecRotation.rayCast(reach, eye)?.entityResult?.entity == target) return@run

            // Get visible point set
            val validHits = mutableMapOf<Vec3d, Rotation>()

            scanVisibleSurfaces(eye, box, resolution = interactionSettings.resolution) { _, vec ->
                if (eye distSq vec > reachSq) return@scanVisibleSurfaces

                val newRotation = eye.rotationTo(vec)

                val cast = newRotation.rayCast(reach, eye) ?: return@scanVisibleSurfaces
                if (cast.entityResult?.entity != target) return@scanVisibleSurfaces

                validHits[vec] = newRotation
            }

            // Switch to the closest visible point
            vec = validHits.minByOrNull { vecRotation dist it.value }?.key ?: return null
        }

        val predictOffset = target.moveDiff * targetPredict
        return RotationContext(eye.rotationTo(vec + predictOffset), rotation)
    }

    private fun SafeContext.runAttack(target: LivingEntity) {
        // Critical hit check
        run {
            if (!criticalSync || attackMode != AttackMode.Cooldown) return@run

            onGroundTicks++
            if (!lastOnGround) onGroundTicks = 0

            val motionY = lastY - prevY
            if (motionY > -0.05 || (lastOnGround && onGroundTicks < 5)) return
        }

        // Cooldown check
        run {
            when (attackMode) {
                AttackMode.Cooldown -> {
                    val attackedTicks = if (delaySync) attackTicks else player.lastAttackedTicks
                    if (attackedTicks < getAttackCooldown() + cooldownOffset) return
                }

                AttackMode.Delay -> {
                    if (System.currentTimeMillis() - lastAttackTime < hitDelay) return
                }
            }
        }

        // Rotation check
        run {
            if (!rotate) return@run
            val angle = RotationManager.currentRotation

            if (interactionSettings.useRayCast) {
                val cast = angle.rayCast(interactionSettings.reach)
                if (cast?.entityResult?.entity != target) return
            }

            // Perform a raycast without checking the environment
            angle.castBox(target.boundingBox, interactionSettings.reach) ?: return
        }

        // Attack
        interaction.attackEntity(player, target)
        if (interactionSettings.swingHand) player.swingHand(Hand.MAIN_HAND)

        lastAttackTime = System.currentTimeMillis()
        hitDelay = random(hitDelay1, hitDelay2) * 50
    }

    private fun SafeContext.getAttackCooldown(): Double {
        val attr = EntityAttributes.ATTACK_SPEED

        // TODO: Fix this
        val attackSpeed = player.getAttributeValue(attr) /*if (!cooldownSync) player.getAttributeValue(attr) else {
            player.mainHandStack.item
                .getAttributeModifiers(EquipmentSlot.MAINHAND)[attr]
                .filter { it.operation == EntityAttributeModifier.Operation.ADDITION }
                .sumOf { it.value } + 4
        }*/

        return 20.0 / attackSpeed
    }

    private fun reset(ctx: SafeContext) = ctx.apply {
        target = null
        attackTicks = player.lastAttackedTicks
        rotation.speedMultiplier = 1.0
        shakeRandom = Vec3d.ZERO

        lastY = 0.0
        prevY = 0.0
        lastOnGround = true
        onGroundTicks = 0

        lastAttackTime = 0L
        hitDelay = 100.0
    }
}
