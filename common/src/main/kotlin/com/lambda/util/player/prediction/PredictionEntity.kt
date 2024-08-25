package com.lambda.util.player.prediction

import com.lambda.Lambda.mc
import com.lambda.context.SafeContext
import com.lambda.interaction.rotation.Rotation
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.flooredPos
import com.lambda.util.math.MathUtils.floorToInt
import com.lambda.util.math.MathUtils.toIntSign
import com.lambda.util.math.MathUtils.toRadian
import com.lambda.util.math.VecUtils
import com.lambda.util.math.VecUtils.minus
import com.lambda.util.math.VecUtils.plus
import com.lambda.util.math.VecUtils.times
import com.lambda.util.player.MovementUtils.motion
import com.lambda.util.player.MovementUtils.moveYaw
import com.lambda.util.player.MovementUtils.movementVector
import net.minecraft.client.input.KeyboardInput
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.enchantment.EnchantmentHelper.getSwiftSneakSpeedBoost
import net.minecraft.entity.Entity
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import kotlin.math.abs
import kotlin.math.floor

// Todo: any player entity support
class PredictionEntity(val player: ClientPlayerEntity) {
    val lastTick get() = PredictionTick(
        position,
        rotation,
        motion,
        boundingBox,
        eyePos,
        isOnGround,
        isJumping,
        this
    )

    // Basics
    private var position = player.pos
    private var motion = player.motion
    private var boundingBox = player.boundingBox

    val eyePos get() = position + Vec3d(0.0, player.standingEyeHeight.toDouble(), 0.0)
    private var rotation = Rotation(player.moveYaw, player.pitch)

    // Secondary movement-related fields
    private var isOnGround = player.isOnGround
    private val isSprinting = player.isSprinting
    private val isSneaking = player.isSneaking
    private var isJumping = false

    // Movement input
    private val input = KeyboardInput(mc.options).apply {
        tick(true, 1f)
    }

    private val pressingJump = input.jumping
    private val forwardMovement = input.movementForward.toDouble()
    private val strafeMovement = input.movementSideways.toDouble()
    private val verticalMovement = pressingJump.toIntSign().toDouble()

    private var forwardSpeed = forwardMovement
    private var strafeSpeed = strafeMovement

    // Other shit
    private var jumpingCooldown = player.jumpingCooldown
    private var velocityAffectingPos = (player.pos - VecUtils.DOWN * 0.001).flooredPos

    private var horizontalCollision = player.horizontalCollision
    private var verticalCollision = player.verticalCollision

    /** @see net.minecraft.client.network.ClientPlayerEntity.tickMovement */
    fun tickMovement() = runSafe {
        forwardSpeed = forwardMovement
        strafeSpeed = strafeMovement

        if (player.isUsingItem) {
            forwardSpeed *= 0.2
            strafeSpeed *= 0.2
        }

        if (isSneaking) {
            val mod = 0.3f + getSwiftSneakSpeedBoost(player)
            forwardSpeed *= mod
            strafeSpeed *= mod
        }

        if (jumpingCooldown > 0) {
            --jumpingCooldown
        }

        val reduceX = abs(motion.x) < 0.03
        val reduceY = abs(motion.y) < 0.03
        val reduceZ = abs(motion.z) < 0.03

        if (reduceX || reduceY || reduceZ) {
            motion = Vec3d(
                if (reduceX) 0.0 else motion.x,
                if (reduceY) 0.0 else motion.y,
                if (reduceZ) 0.0 else motion.z
            )
        }

        isJumping = false

        if (pressingJump && isOnGround && jumpingCooldown == 0) {
            jumpingCooldown = 10
            isJumping = true
            jump()
        }

        forwardSpeed *= 0.98
        strafeSpeed *= 0.98

        travel()
    }

    /** @see net.minecraft.entity.LivingEntity.travel */
    private fun SafeContext.travel() {
        val travelVec = Vec3d(strafeSpeed, verticalMovement, forwardSpeed)

        val gravity = when {
            motion.y < 0.0 && player.hasStatusEffect(StatusEffects.SLOW_FALLING) -> 0.01
            else -> 0.08
        }

        val slipperiness = velocityAffectingPos.blockState(world).block.slipperiness.toDouble()
        var friction = 0.91

        if (isOnGround) {
            friction *= slipperiness
        }

        applyMovementInput(travelVec, slipperiness)
        move()

        motion += VecUtils.DOWN * gravity
        motion *= Vec3d(friction, 0.98, friction)
    }

    /** @see net.minecraft.entity.LivingEntity.applyMovementInput */
    private fun SafeContext.applyMovementInput(travelVec: Vec3d, slipperiness: Double) {
        val movementSpeed = run {
            /** @see net.minecraft.entity.LivingEntity.getMovementSpeed */

            val slipperinessCubed = slipperiness * slipperiness * slipperiness
            val movementSpeed = player.movementSpeed.toDouble()

            val groundSpeed = movementSpeed * (0.216 / slipperinessCubed)
            val airSpeed = if (isSprinting) 0.026 else 0.02

            if (isOnGround) groundSpeed else airSpeed
        }.toFloat()

        /** @see net.minecraft.entity.Entity.updateVelocity */
        motion += Entity.movementInputToVelocity(travelVec, movementSpeed, rotation.yawF)
    }

    /** @see net.minecraft.entity.Entity.move */
    private fun SafeContext.move() {
        var movement = motion
        movement = adjustMovementForCollisions(movement)

        if (movement.lengthSquared() > 1.0E-7) {
            position += movement
        }

        val xCollide = !MathHelper.approximatelyEquals(movement.x, motion.x)
        val yCollide = !MathHelper.approximatelyEquals(movement.y, motion.y)
        val zCollide = !MathHelper.approximatelyEquals(movement.z, motion.z)

        horizontalCollision = xCollide || zCollide
        verticalCollision = yCollide

        isOnGround = yCollide && movement.y < 0.0

        if (horizontalCollision) {
            motion = Vec3d(
                if (xCollide) 0.0 else motion.x,
                motion.y,
                if (zCollide) 0.0 else motion.z
            )
        }

        val velocityMultiplier = run {
            val f = position.flooredPos.blockState(world).block.velocityMultiplier.toDouble()
            val g = velocityAffectingPos.blockState(world).block.velocityMultiplier.toDouble()
            if (f == 1.0) g else f
        }

        motion *= Vec3d(velocityMultiplier, 1.0, velocityMultiplier)

        boundingBox.apply {
            val normalized = offset(-minX, -minY, -minZ).offset(-lengthX * 0.5, 0.0, -lengthZ * 0.5)
            boundingBox = normalized.offset(position)
        }

        val y = (floor(position.y) - 0.00001).floorToInt()
        velocityAffectingPos = BlockPos(position.x.floorToInt(), y, position.z.floorToInt())
    }

    /** @see net.minecraft.entity.LivingEntity.jump */
    private fun SafeContext.jump() {
        if (isSprinting) {
            val yawRad = rotation.yaw.toRadian()
            motion += movementVector(yawRad, 0.0) * 0.2
        }

        /** @see net.minecraft.entity.Entity.getJumpVelocityMultiplier */
        /** @see net.minecraft.entity.Entity.getJumpVelocityMultiplier */
        val jumpHeight = run {
            val f = position.flooredPos.blockState(world).block.jumpVelocityMultiplier.toDouble()
            val g = velocityAffectingPos.blockState(world).block.jumpVelocityMultiplier.toDouble()
            if (f == 1.0) g else f
        } * 0.42 + player.jumpBoostVelocityModifier

        motion += Vec3d(0.0, jumpHeight, 0.0)
    }

    /** @see net.minecraft.entity.Entity.adjustMovementForCollisions */
    private fun SafeContext.adjustMovementForCollisions(movement: Vec3d): Vec3d {
        if (movement.lengthSquared() == 0.0) {
            return movement
        }

        val prevPos = player.pos
        val prevBox = player.boundingBox

        player.pos = position
        player.boundingBox = boundingBox

        val list = world.getEntityCollisions(player, boundingBox.stretch(movement))
        val processed = Entity.adjustMovementForCollisions(player, movement, boundingBox, world, list)

        player.pos = prevPos
        player.boundingBox = prevBox

        return processed
    }
}