package com.lambda.pathing.world.edit

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.physics.BlockUse
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.world.snapshot.CellEdit
import com.lambda.pathing.world.snapshot.EditableEnvironment
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot

data class ToggleTarget(val pos: BlockPos, val open: Boolean, val segment: Int, val after: Int = 0)

object DoorRules {
	fun eye(state: MovementSimulationState, profile: PlayerPhysicsProfile): Vec3d {
		val height = state.boundingBox.lengthY
		val eyeHeight = when {
			abs(height - profile.height) < POSE_TOLERANCE -> profile.standingEyeHeight
			abs(height - profile.crouchHeight) < POSE_TOLERANCE -> profile.crouchingEyeHeight
			else -> profile.swimmingEyeHeight
		}
		return Vec3d(state.position.x, state.position.y + eyeHeight, state.position.z)
	}

	fun look(rotation: Rotation): Vec3d {
		val pitch = rotation.pitchF * (Math.PI.toFloat() / 180.0f)
		val yaw = -rotation.yawF * (Math.PI.toFloat() / 180.0f)
		val cosYaw = MathHelper.cos(yaw.toDouble())
		val sinYaw = MathHelper.sin(yaw.toDouble())
		val cosPitch = MathHelper.cos(pitch.toDouble())
		val sinPitch = MathHelper.sin(pitch.toDouble())
		return Vec3d((sinYaw * cosPitch).toDouble(), (-sinPitch).toDouble(), (cosYaw * cosPitch).toDouble())
	}

	fun target(environment: EditableEnvironment, state: MovementSimulationState, profile: PlayerPhysicsProfile): BlockHitResult? {
		val eye = eye(state, profile)
		return SnapshotRaycast.cast(environment, eye, eye.add(look(state.rotation).multiply(profile.blockReach)))
	}

	fun boxes(environment: EditableEnvironment, pos: BlockPos): List<Box> {
		val cell = environment.readBlock(pos.x, pos.y, pos.z) ?: return emptyList()
		val pairedY = cell.toggle?.pairedY ?: 0
		return cell.rayShape.boundingBoxes.map { it.offset(pos) } +
				(if (pairedY == 0) emptyList() else environment.readBlock(pos.x, pos.y + pairedY, pos.z)?.rayShape?.boundingBoxes.orEmpty().map { it.offset(pos.x.toDouble(), (pos.y + pairedY).toDouble(), pos.z.toDouble()) })
	}

	fun visiblePoint(environment: EditableEnvironment, eye: Vec3d, pos: BlockPos, reach: Double, prefer: Rotation): Vec3d? {
		val candidates = ArrayList<Pair<Vec3d, Rotation>>()
		for (box in boxes(environment, pos)) for (side in Direction.entries) {
			if (!faces(box, side, eye) || covered(environment, box, side)) continue
			for (first in SAMPLES) for (second in SAMPLES) {
				val point = facePoint(box, side, first, second)
				if (point.squaredDistanceTo(eye) > reach * reach) continue
				candidates += point to lookAt(eye, point)
			}
		}
		candidates.sortBy { (_, look) -> Rotation.wrap(look.yaw - prefer.yaw).let { yaw -> yaw * yaw } + (look.pitch - prefer.pitch).let { pitch -> pitch * pitch } }
		for ((point, look) in candidates.take(MAX_RAYS)) if (hits(environment, eye, look, pos, reach)) return point
		return null
	}

	fun hits(environment: EditableEnvironment, eye: Vec3d, rotation: Rotation, pos: BlockPos, reach: Double): Boolean {
		val hit = SnapshotRaycast.cast(environment, eye, eye.add(look(rotation).multiply(reach))) ?: return false
		return covers(environment, pos, hit.blockPos)
	}

	private fun covered(environment: EditableEnvironment, box: Box, side: Direction): Boolean {
		val face = when (side) {
			Direction.EAST -> box.maxX
			Direction.WEST -> box.minX
			Direction.UP -> box.maxY
			Direction.DOWN -> box.minY
			Direction.SOUTH -> box.maxZ
			Direction.NORTH -> box.minZ
		}
		if (face != floor(face)) return false
		val inside = BlockPos(MathHelper.floor((box.minX + box.maxX) / 2), MathHelper.floor((box.minY + box.maxY) / 2), MathHelper.floor((box.minZ + box.maxZ) / 2))
		val neighbor = inside.offset(side)
		val shape = environment.readBlock(neighbor.x, neighbor.y, neighbor.z)?.rayShape ?: return true
		return shape.boundingBoxes.singleOrNull() == FULL
	}

	private val FULL = Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)

	private fun faces(box: Box, side: Direction, eye: Vec3d): Boolean = when (side) {
		Direction.EAST -> eye.x > box.maxX
		Direction.WEST -> eye.x < box.minX
		Direction.UP -> eye.y > box.maxY
		Direction.DOWN -> eye.y < box.minY
		Direction.SOUTH -> eye.z > box.maxZ
		Direction.NORTH -> eye.z < box.minZ
	}

	private fun facePoint(box: Box, side: Direction, first: Double, second: Double): Vec3d = when (side.axis) {
		Direction.Axis.X -> Vec3d(if (side == Direction.EAST) box.maxX else box.minX, lerp(box.minY, box.maxY, first), lerp(box.minZ, box.maxZ, second))
		Direction.Axis.Y -> Vec3d(lerp(box.minX, box.maxX, first), if (side == Direction.UP) box.maxY else box.minY, lerp(box.minZ, box.maxZ, second))
		else -> Vec3d(lerp(box.minX, box.maxX, first), lerp(box.minY, box.maxY, second), if (side == Direction.SOUTH) box.maxZ else box.minZ)
	}

	private fun lerp(from: Double, to: Double, at: Double) = from + (to - from) * at

	fun lookAt(eye: Vec3d, point: Vec3d): Rotation {
		val dx = point.x - eye.x
		val dy = point.y - eye.y
		val dz = point.z - eye.z
		return Rotation(Math.toDegrees(atan2(dz, dx)) - 90.0, Math.toDegrees(atan2(-dy, hypot(dx, dz))))
	}

	fun covers(environment: EditableEnvironment, door: BlockPos, pos: BlockPos): Boolean =
		door == pos || environment.readBlock(door.x, door.y, door.z)?.toggle?.let { it.pairedY != 0 && door.x == pos.x && door.z == pos.z && door.y + it.pairedY == pos.y } == true

	fun span(environment: EditableEnvironment, door: BlockPos): ClosedFloatingPointRange<Double> {
		val paired = environment.readBlock(door.x, door.y, door.z)?.toggle?.pairedY ?: 0
		return door.y + minOf(0, paired).toDouble()..door.y + 1.0 + maxOf(0, paired)
	}

	fun use(environment: EditableEnvironment, state: MovementSimulationState, profile: PlayerPhysicsProfile, doors: Collection<BlockPos>): BlockUse? {
		if (state.isSneaking) return null
		val hit = target(environment, state, profile) ?: return null
		val pos = hit.blockPos
		if (doors.none { covers(environment, it, pos) }) return null
		val toggle = environment.readBlock(pos.x, pos.y, pos.z)?.toggle ?: return null
		return BlockUse(pos.toImmutable(), hit.side, hit.pos, !toggle.open)
	}

	fun edits(environment: EditableEnvironment, state: MovementSimulationState, profile: PlayerPhysicsProfile, use: BlockUse): List<CellEdit>? {
		val toggle = environment.readBlock(use.pos.x, use.pos.y, use.pos.z)?.toggle ?: return null
		if (toggle.open == use.open) return emptyList()
		if (state.isSneaking) return null
		val hit = target(environment, state, profile) ?: return null
		if (hit.blockPos != use.pos || hit.side != use.side || hit.pos != use.hit) return null
		val edits = arrayListOf(CellEdit(use.pos.x, use.pos.y, use.pos.z, toggle.toggled))
		if (toggle.pairedY != 0) {
			val y = use.pos.y + toggle.pairedY
			environment.readBlock(use.pos.x, y, use.pos.z)?.toggle?.let { edits += CellEdit(use.pos.x, y, use.pos.z, it.toggled) }
		}
		return edits
	}

	private const val POSE_TOLERANCE = 1e-3

	private val SAMPLES = doubleArrayOf(0.25, 0.5, 0.75)

	private const val MAX_RAYS = 16
}
