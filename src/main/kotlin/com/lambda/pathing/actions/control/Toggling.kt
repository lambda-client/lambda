package com.lambda.pathing.actions.control

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.physics.Turning
import com.lambda.pathing.world.edit.DoorRules
import com.lambda.pathing.world.edit.ToggleTarget
import com.lambda.pathing.world.snapshot.EditableEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShape
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.round
import kotlin.math.sin

internal class Toggling(
	private val inner: ControlProgram,
	private var world: EditableEnvironment,
	private val profile: PlayerPhysicsProfile,
	private val turning: Turning,
	private val targets: List<ToggleTarget>,
	private val chain: List<Stance> = emptyList(),
	private val original: EditableEnvironment? = null,
	changed: List<BlockPos> = emptyList(),
	steering: Rotation? = null,
) : ControlProgram {
	private var frame: Rotation? = steering
	private var frameTurn = Rotation.ZERO
	private var progress = 0
	private val restorable = (changed + targets.map { it.pos }).distinct()
	private val byCell = targets.groupBy { it.pos }.values.map { list -> list.sortedBy { it.segment } }
	private val sights = HashMap<BlockPos, Sight>()
	private val points = HashMap<BlockPos, Vec3d>()

	override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput = control(frame, observed).copy(steering = this.frame)

	private fun control(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
		val steering = this.frame
		val input = inner.input(frame, if (steering == null) observed else observed.copy(rotation = steering, turn = frameTurn))
		if (steering != null) {
			val next = input.rotation ?: steering
			frameTurn = Rotation(Rotation.wrap(next.yaw - steering.yaw), next.pitch - steering.pitch)
			this.frame = next
		}
		advance(observed)
		val pending = pending()
		val restores = restores(observed)
		val usable = usable(observed, pending)
		val use = if (usable.isEmpty() && restores.isEmpty()) null else DoorRules.use(world, observed, profile, usable + restores)
		if (use == null) return aim(observed, input, usable, aimable(pending), restores)
		DoorRules.edits(world, observed, profile, use)?.let { world = world.edited(it) }
		val next = pending()
		return aim(observed, input, usable(observed, next), aimable(next), restores(observed)).copy(use = use)
	}

	private fun usable(observed: MovementSimulationState, pending: List<ToggleTarget>): List<BlockPos> =
		pending.filter { progress >= it.after && fits(observed, it) }.map { it.pos }

	private fun pending(): List<ToggleTarget> = byCell.mapNotNull { list ->
		val target = list.firstOrNull { it.segment + 1 >= progress } ?: return@mapNotNull null
		target.takeIf { world.readBlock(it.pos.x, it.pos.y, it.pos.z)?.toggle?.let { toggle -> toggle.open != target.open } == true }
	}

	private fun aimable(pending: List<ToggleTarget>): List<BlockPos> = pending.filter { progress + 1 >= it.after }.map { it.pos }

	private fun fits(observed: MovementSimulationState, target: ToggleTarget): Boolean {
		val toggle = world.readBlock(target.pos.x, target.pos.y, target.pos.z)?.toggle ?: return false
		val shape = (if (target.open) toggle.opened else toggle.closed).collisionShape
		return clear(observed, target.pos, shape, 0.0) && (toggle.pairedY == 0 || clear(observed, target.pos.up(toggle.pairedY), shape, 0.0))
	}

	private fun advance(observed: MovementSimulationState) {
		for (index in progress + 1 until chain.size) {
			val stance = chain[index]
			if (hypot(stance.x + 0.5 - observed.position.x, stance.z + 0.5 - observed.position.z) < REACHED && abs(stance.y - observed.position.y) < REACHED_RISE) progress = index
		}
	}

	private fun restores(observed: MovementSimulationState): List<BlockPos> {
		val original = original ?: return emptyList()
		return restorable.filter { pos ->
			val was = original.readBlock(pos.x, pos.y, pos.z)?.toggle ?: return@filter false
			val now = world.readBlock(pos.x, pos.y, pos.z)?.toggle ?: return@filter false
			now.open != was.open && behind(pos) && leaving(observed, pos) && clear(observed, pos, was.current.collisionShape)
		}
	}

	private fun behind(pos: BlockPos): Boolean = targets.none { target ->
		target.pos.x == pos.x && target.pos.z == pos.z && abs(target.pos.y - pos.y) <= 1 && target.segment + PASSED_SEGMENTS > progress
	}

	private fun leaving(observed: MovementSimulationState, pos: BlockPos): Boolean =
		(pos.x + 0.5 - observed.position.x) * observed.velocity.x + (pos.z + 0.5 - observed.position.z) * observed.velocity.z <= 0.0

	private fun clear(observed: MovementSimulationState, pos: BlockPos, shape: VoxelShape, clearance: Double = CLEARANCE): Boolean {
		val body = observed.boundingBox.expand(clearance)
		return shape.boundingBoxes.none { it.offset(pos).intersects(body) }
	}

	private fun aim(
		observed: MovementSimulationState,
		input: MovementSimulationInput,
		usable: List<BlockPos>,
		aimable: List<BlockPos>,
		restores: List<BlockPos>,
	): MovementSimulationInput {
		val rotation = input.rotation ?: return input
		val sprinting = sprinting(observed, input)
		if (observed.touchingWater || !wholeKeys(input) || sprinting && input.jump && observed.onGround) return release(observed, input)
		val eye = DoorRules.eye(observed, profile)
		val heading = heading(rotation, input)
		val offsets = if (sprinting) SPRINT_OFFSETS else WALK_OFFSETS
		val reach = profile.blockReach + AIM_LEAD
		for (pos in usable) {
			look(eye, pos, reach, heading, offsets, observed.rotation)?.let { return steer(observed, input, rotation, it, heading, sprinting) }
			if (sprinting) look(eye, pos, reach, heading, WALK_OFFSETS, observed.rotation)?.let { return steer(observed, input, rotation, it, heading, false) }
		}
		aimable.firstNotNullOfOrNull { pos -> if (pos in usable) null else look(eye, pos, reach, heading, offsets, observed.rotation) }
			?.let { return steer(observed, input, rotation, it, heading, sprinting) }
		restores.firstNotNullOfOrNull { look(eye, it, profile.blockReach, heading, WALK_OFFSETS, observed.rotation, RESTORE_DRIFT) }
			?.let { return steer(observed, input, rotation, it, heading, false) }
		return release(observed, input)
	}

	private fun steer(observed: MovementSimulationState, input: MovementSimulationInput, rotation: Rotation, look: Rotation, heading: Double?, sprinting: Boolean): MovementSimulationInput {
		if (frame == null) frame = rotation
		val turned = turning.turn(observed, look)
		return if (heading == null) input.copy(rotation = turned) else keyed(input, turned, heading, sprinting)
	}

	private fun look(eye: Vec3d, pos: BlockPos, reach: Double, heading: Double?, offsets: List<Double>, current: Rotation, drift: Double = MAX_DRIFT): Rotation? {
		if (!inReach(eye, pos, reach)) return null
		if (heading == null) return free(eye, pos, reach, current)
		val boxes = DoorRules.boxes(world, pos)
		val spans = boxes.map { span(eye, it) }
		sights[pos]?.takeIf { it.offset in offsets && it.box < boxes.size }?.let { kept ->
			aimed(eye, boxes[kept.box], spans[kept.box], heading - kept.offset, kept.height, reach)
				?.takeIf { it.drift <= drift && DoorRules.hits(world, eye, it.look, pos, reach) }
				?.let { return it.look }
		}
		val candidates = ArrayList<Pair<Sight, Aim>>()
		for (offset in offsets) for ((index, box) in boxes.withIndex()) {
			if (spans[index].drift(heading - offset) > drift) continue
			for (height in HEIGHTS) {
				val aim = aimed(eye, box, spans[index], heading - offset, height, reach) ?: continue
				candidates += Sight(offset, index, height) to aim
			}
		}
		candidates.sortWith(compareBy({ round(it.second.drift) }, { turn(current, it.second.look) }))
		for ((sight, aim) in candidates.take(MAX_RAYS)) {
			if (!DoorRules.hits(world, eye, aim.look, pos, reach)) continue
			sights[pos] = sight
			return aim.look
		}
		sights.remove(pos)
		return null
	}

	private fun span(eye: Vec3d, box: Box): Span {
		val center = yawTo(eye, (box.minX + box.maxX) / 2, (box.minZ + box.maxZ) / 2)
		if (eye.x > box.minX && eye.x < box.maxX && eye.z > box.minZ && eye.z < box.maxZ) return Span(center, null, null)
		var low = Double.POSITIVE_INFINITY
		var high = Double.NEGATIVE_INFINITY
		for (x in doubleArrayOf(box.minX, box.maxX)) for (z in doubleArrayOf(box.minZ, box.maxZ)) {
			val relative = Rotation.wrap(yawTo(eye, x, z) - center)
			low = minOf(low, relative)
			high = maxOf(high, relative)
		}
		val inset = (high - low) * SPAN_INSET
		return Span(center, low + inset, high - inset)
	}

	private fun aimed(eye: Vec3d, box: Box, span: Span, ideal: Double, height: Double, reach: Double): Aim? {
		val yaw = span.yaw(ideal)
		val radians = Math.toRadians(yaw)
		val distance = crossing(eye, box, -sin(radians), cos(radians)) ?: return null
		val y = box.minY + (box.maxY - box.minY) * height
		if (distance * distance + (eye.y - y) * (eye.y - y) > reach * reach) return null
		return Aim(Rotation(yaw, Math.toDegrees(MathHelper.atan2(eye.y - y, distance))), abs(Rotation.wrap(yaw - ideal)))
	}

	private fun yawTo(eye: Vec3d, x: Double, z: Double): Double = Math.toDegrees(MathHelper.atan2(z - eye.z, x - eye.x)) - 90.0

	private fun crossing(eye: Vec3d, box: Box, towardX: Double, towardZ: Double): Double? {
		val alongX = slab(eye.x, towardX, box.minX, box.maxX) ?: return null
		val alongZ = slab(eye.z, towardZ, box.minZ, box.maxZ) ?: return null
		val enter = maxOf(alongX.start, alongZ.start, 0.0)
		val exit = minOf(alongX.endInclusive, alongZ.endInclusive)
		return if (exit <= enter) null else (enter + exit) / 2
	}

	private fun slab(from: Double, toward: Double, low: Double, high: Double): ClosedFloatingPointRange<Double>? {
		if (abs(toward) < PARALLEL) return if (from > low && from < high) Double.NEGATIVE_INFINITY..Double.POSITIVE_INFINITY else null
		val first = (low - from) / toward
		val second = (high - from) / toward
		return minOf(first, second)..maxOf(first, second)
	}

	private fun free(eye: Vec3d, pos: BlockPos, reach: Double, current: Rotation): Rotation? {
		points[pos]?.takeIf { it.squaredDistanceTo(eye) <= reach * reach }?.let { point ->
			val look = DoorRules.lookAt(eye, point)
			if (DoorRules.hits(world, eye, look, pos, reach)) return look
		}
		val point = DoorRules.visiblePoint(world, eye, pos, reach, current)
		if (point == null) points.remove(pos) else points[pos] = point
		return point?.let { DoorRules.lookAt(eye, it) }
	}

	private fun turn(from: Rotation, to: Rotation): Double = abs(Rotation.wrap(to.yaw - from.yaw)) + abs(to.pitch - from.pitch)

	private fun inReach(eye: Vec3d, pos: BlockPos, reach: Double): Boolean {
		val dx = (eye.x - (pos.x + 0.5)).coerceIn(-0.5, 0.5).let { eye.x - (pos.x + 0.5) - it }
		val dy = (eye.y - (pos.y + 0.5)).coerceIn(-0.5, 0.5).let { eye.y - (pos.y + 0.5) - it }
		val dz = (eye.z - (pos.z + 0.5)).coerceIn(-0.5, 0.5).let { eye.z - (pos.z + 0.5) - it }
		return dx * dx + dy * dy + dz * dz <= reach * reach
	}

	private fun release(observed: MovementSimulationState, input: MovementSimulationInput): MovementSimulationInput {
		val steering = frame ?: return input
		val rotation = input.rotation ?: steering
		if (turning.reached(observed.rotation, rotation) && abs(observed.turn.yaw) + abs(observed.turn.pitch) <= RELEASE_TURN) {
			frame = null
			return input
		}
		val look = turning.turn(observed, rotation)
		val heading = heading(rotation, input) ?: return input.copy(rotation = look)
		return keyed(input, look, heading, sprinting(observed, input) && abs(Rotation.wrap(heading - look.yaw)) <= SPRINT_KEYS_SPAN)
	}

	private fun keyed(input: MovementSimulationInput, look: Rotation, heading: Double, sprinting: Boolean): MovementSimulationInput {
		var forward = input.forward
		var strafe = input.strafe
		var bestError = Double.MAX_VALUE
		for ((f, s) in KEYS) {
			if (sprinting && f <= 0.0) continue
			val error = abs(Rotation.wrap(look.yaw + angle(f, s) - heading))
			if (error < bestError) {
				bestError = error
				forward = f
				strafe = s
			}
		}
		return input.copy(rotation = look, forward = forward, strafe = strafe)
	}

	private fun heading(rotation: Rotation, input: MovementSimulationInput): Double? =
		if (input.forward == 0.0 && input.strafe == 0.0) null else rotation.yaw + angle(input.forward, input.strafe)

	private fun sprinting(observed: MovementSimulationState, input: MovementSimulationInput): Boolean = input.sprint || observed.isSprinting

	private fun wholeKeys(input: MovementSimulationInput): Boolean =
		input.forward in WHOLE && input.strafe in WHOLE

	private class Sight(val offset: Double, val box: Int, val height: Double)

	private class Span(val center: Double, val low: Double?, val high: Double?) {
		fun yaw(ideal: Double): Double =
			if (low == null || high == null) ideal else center + Rotation.wrap(ideal - center).coerceIn(low, maxOf(low, high))

		fun drift(ideal: Double): Double = abs(Rotation.wrap(yaw(ideal) - ideal))
	}

	private class Aim(val look: Rotation, val drift: Double)

	private companion object {
		const val AIM_LEAD = 1.0
		const val MAX_DRIFT = 0.5
		const val RESTORE_DRIFT = 22.5
		const val SPAN_INSET = 0.2
		const val MAX_RAYS = 12
		const val PARALLEL = 1e-9
		const val RELEASE_TURN = 1.0
		const val REACHED = 0.75
		const val REACHED_RISE = 1.5
		const val PASSED_SEGMENTS = 2
		const val CLEARANCE = 0.1
		const val SPRINT_KEYS_SPAN = 67.5
		val WHOLE = setOf(-1.0, 0.0, 1.0)
		val KEYS = listOf(1.0 to 0.0, 1.0 to 1.0, 1.0 to -1.0, 0.0 to 1.0, 0.0 to -1.0, -1.0 to 1.0, -1.0 to -1.0, -1.0 to 0.0)
		val SPRINT_OFFSETS = KEYS.filter { it.first > 0.0 }.map { angle(it.first, it.second) }
		val WALK_OFFSETS = KEYS.map { angle(it.first, it.second) }
		val HEIGHTS = doubleArrayOf(0.5, 0.25, 0.75)

		fun angle(forward: Double, strafe: Double): Double = -Math.toDegrees(atan2(strafe, forward))
	}
}
