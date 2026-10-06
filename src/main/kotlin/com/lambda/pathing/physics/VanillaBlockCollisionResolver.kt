package com.lambda.pathing.physics

import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d

internal object VanillaBlockCollisionResolver {
	fun adjust(
		movement: Vec3d,
		boundingBox: Box,
		onGround: Boolean,
		stepHeight: Double,
		collect: (Box, BlockCollisions) -> Unit,
	): Vec3d {
		if (movement.lengthSquared() == 0.0) return movement

		val buffer = BUFFERS.get()
		fun collisionsIn(query: Box): BlockCollisions = buffer.also { it.clear(); collect(query, it) }

		val normalShapes = collisionsIn(boundingBox.stretch(movement))
		val normal = clip(movement, boundingBox, normalShapes)
		val xBlocked = movement.x != normal.x
		val yBlocked = movement.y != normal.y
		val zBlocked = movement.z != normal.z
		val landedDuringMove = yBlocked && movement.y < 0.0

		if (stepHeight <= 0.0 || (!landedDuringMove && !onGround) || (!xBlocked && !zBlocked)) {
			return normal
		}

		val stepBase = if (landedDuringMove) boundingBox.offset(0.0, normal.y, 0.0) else boundingBox
		var stepQuery = stepBase.stretch(movement.x, stepHeight, movement.z)
		if (!landedDuringMove) {
			stepQuery = stepQuery.stretch(0.0, (-Vanilla.STEP_PROBE_DEPTH).toDouble(), 0.0)
		}

		val stepShapes = collisionsIn(stepQuery)
		val baseVerticalOffset = normal.y.toFloat()
		for (candidateHeight in stepShapes.stepHeights(stepBase.minY, stepHeight.toFloat(), baseVerticalOffset)) {
			val candidate = clip(
				movement = Vec3d(movement.x, candidateHeight.toDouble(), movement.z),
				boundingBox = stepBase,
				collisions = stepShapes,
			)
			if (candidate.horizontalLengthSquared() > normal.horizontalLengthSquared()) {
				val landingAdjustment = boundingBox.minY - stepBase.minY
				return candidate.subtract(0.0, landingAdjustment, 0.0)
			}
		}

		return normal
	}

	private fun clip(
		movement: Vec3d,
		boundingBox: Box,
		collisions: BlockCollisions,
	): Vec3d {
		if (collisions.isEmpty) return movement

		var adjusted = Vec3d.ZERO
		for (axis in Direction.getCollisionOrder(movement)) {
			val component = movement.getComponentAlongAxis(axis)
			if (component == 0.0) continue
			val offset = collisions.maxOffset(axis, boundingBox.offset(adjusted), component)
			adjusted = adjusted.withAxis(axis, offset)
		}
		return adjusted
	}

	private val BUFFERS: ThreadLocal<BlockCollisions> = ThreadLocal.withInitial(::BlockCollisions)
}
