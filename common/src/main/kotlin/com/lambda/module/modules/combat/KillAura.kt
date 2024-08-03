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
import com.lambda.util.math.MathUtils.lerp
import com.lambda.util.math.MathUtils.random
import com.lambda.util.math.VecUtils.distSq
import com.lambda.util.math.VecUtils.minus
import com.lambda.util.math.VecUtils.plus
import com.lambda.util.math.VecUtils.times
import com.lambda.util.player.MovementUtils.moveDiff
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
            },
            onReceive = {
                target?.let { entity ->
                    runAttack(entity)
                }
            }
        )

        listener<PlayerPacketEvent.Pre>(Int.MIN_VALUE) { event ->
            prevY = lastY
            lastY = event.position.y
            lastOnGround = event.onGround
        }

        listener<TickEvent.Pre> {
            target = targeting.getTarget()
            if (!timerSync) attackTicks++

            if (!rotate) {
                target?.let { entity ->
                    runAttack(entity)
                }
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

        val eye = player.getCameraPosVec(1f)
        val box = target.boundingBox

        val reach = targeting.targetingRange + 2.0
        val reachSq = reach.pow(2)

        // Do not rotate if the eyes are inside the target's AABB
        if (box.contains(eye)) {
            return RotationContext(currentRotation, rotation)
        }

        // Rotation stabilizer
        rotation.speedMultiplier = if (stabilize && !rotation.instant) {
            val slowDown = currentRotation.castBox(box, reach) != null

            with(rotation) {
                val targetSpeed = if (slowDown) 0.0 else 1.0
                val acceleration = if (slowDown) 0.2 else 0.1

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
            lerp(box.minX, box.maxX, shakeRandom.x),
            lerp(box.minY, box.maxY, shakeRandom.x),
            lerp(box.minZ, box.maxZ, shakeRandom.x)
        )

        vec = lerp(vec, box.center, centerFactor) // Mix with center
        vec = lerp(vec, random, shakeFactor) // Apply shaking

        // Raycast
        run {
            if (!interactionSettings.useRayCast) return@run

            val vecRotation = eye.rotationTo(vec)
            if (vecRotation.rayCast(reach, eye)?.entityResult?.entity == target) return@run

            // Get visible point set
            val validHits = mutableMapOf<Vec3d, Rotation>()

            scanVisibleSurfaces(eye, box, emptySet(), interactionSettings.resolution) { _, vec ->
                if (eye distSq vec > reachSq) return@scanVisibleSurfaces

                val newRotation = eye.rotationTo(vec)

                val cast = newRotation.rayCast(reach, eye) ?: return@scanVisibleSurfaces
                if (cast.entityResult?.entity != target) return@scanVisibleSurfaces

                validHits[vec] = newRotation
            }

            // Switch to the closest visible point
            vec = validHits.minByOrNull { vecRotation dist it.value }?.key ?: return null
        }

        val predictOffset = target.moveDiff * targetPredict - player.moveDiff * Vec3d(1.0, -0.5, 1.0) * selfPredict
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
        val attr = EntityAttributes.GENERIC_ATTACK_SPEED

        val attackSpeed = if (!cooldownSync) player.getAttributeValue(attr) else {
            player.mainHandStack.item
                .getAttributeModifiers(EquipmentSlot.MAINHAND)[attr]
                .filter { it.operation == EntityAttributeModifier.Operation.ADDITION }
                .sumOf { it.value } + 4
        }

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