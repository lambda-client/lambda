package com.lambda.pathing.debug

import com.lambda.Lambda.mc
import com.lambda.pathing.execution.ExecutionDeviation
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.Vanilla
import com.lambda.pathing.search.PublishedPath
import com.lambda.pathing.world.snapshot.BlockPhysicsCapture
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.block.ShapeContext
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d

internal fun executionRejectionReport(
	path: PublishedPath,
	frame: Int,
	deviation: ExecutionDeviation,
	observed: MovementSimulationState,
	afterInput: Boolean,
	snapshot: SnapshotSimulationEnvironment?,
	ignoreHazards: Boolean,
): String {
	val splice = when {
		frame in path.spliceFrames -> " at predicted splice"
		frame + 1 in path.spliceFrames -> " immediately before predicted splice"
		else -> ""
	}
	val tape = path.plan.tape.asList()
	val input = tape.getOrNull(frame)
	val before = if (frame == 0) path.plan.initialState else path.plan.frames.getOrNull(frame - 1)?.state
	val expected = if (afterInput) path.plan.frames.getOrNull(frame)?.state else before
	val phase = if (afterInput) "after input" else "before input"
	val inputDetail = input?.let {
		"; input f=%.1f s=%.1f jump=%s sprintKey=%s sneakKey=%s yaw=%s".format(
			it.forward, it.strafe, it.jump, it.sprint, it.sneak,
			it.rotation?.yaw?.let { yaw -> "%.2f".format(yaw) } ?: "hold",
		)
	}.orEmpty()
	val stateDetail = expected?.let {
		("; expected/live yaw %.3f/%.3f, sprint %s/%s, sneaking %s/%s, ground %s/%s, " +
				"hCollision %s/%s, soft %s/%s, vCollision %s/%s, jumpCooldown %d/%d, " +
				"position %s/%s, velocity %s/%s").format(
			it.rotation.yaw, observed.rotation.yaw,
			it.isSprinting, observed.isSprinting,
			it.isSneaking, observed.isSneaking,
			it.onGround, observed.onGround,
			it.horizontalCollision, observed.horizontalCollision,
			it.collidedSoftly, observed.collidedSoftly,
			it.verticalCollision, observed.verticalCollision,
			it.jumpingCooldown, observed.jumpingCooldown,
			it.position.short(), observed.position.short(),
			it.velocity.short(), observed.velocity.short(),
		)
	}.orEmpty()
	val ledgeDetail =
		if (expected?.horizontalCollision == true && !observed.horizontalCollision && before != null) {
			liveLedgeProbe(before.boundingBox, observed.velocity)
		} else ""
	val previousInputDetail = tape.getOrNull(frame - 1)?.let {
		"; previous input f=%.1f jump=%s sprintKey=%s sneakKey=%s".format(
			it.forward, it.jump, it.sprint, it.sneak,
		)
	}.orEmpty()
	val liveInputDetail = mc.player?.input?.let {
		"; live input f=%.1f jump=%s sprintKey=%s sneakKey=%s".format(
			it.movementVector.y, it.playerInput.jump(), it.playerInput.sprint(),
			it.playerInput.sneak(),
		)
	}.orEmpty()
	val worldDetail = if (deviation is ExecutionDeviation.WorldChanged || snapshot == null) "" else {
		snapshotDifferences(snapshot, listOfNotNull(before?.boundingBox, expected?.boundingBox, observed.boundingBox), ignoreHazards)
	}
	return "frame $frame $phase$splice: $deviation$inputDetail$previousInputDetail" +
			"$liveInputDetail$stateDetail$ledgeDetail$worldDetail${touchingEntities()}"
}

private fun touchingEntities(): String {
	val player = mc.player ?: return ""
	val world = mc.world ?: return ""
	val touching = world.getCrammedEntities(player, player.boundingBox.expand(CONTACT_REACH))
	if (touching.isEmpty()) return ""
	return "; touching " + touching.take(MAX_REPORTED_ENTITIES).joinToString(", ") { "${it.type.untranslatedName} at ${it.pos.short()}" }
}

private fun snapshotDifferences(snapshot: SnapshotSimulationEnvironment, boxes: List<Box>, ignoreHazards: Boolean): String {
	val player = mc.player ?: return ""
	val world = mc.world ?: return ""
	val around = boxes.reduce(Box::union).expand(1.0)
	val context = ShapeContext.of(player)
	val differences = ArrayList<String>()
	val cell = BlockPos.Mutable()
	for (x in MathHelper.floor(around.minX)..MathHelper.floor(around.maxX)) {
		for (y in MathHelper.floor(around.minY)..MathHelper.floor(around.maxY)) {
			for (z in MathHelper.floor(around.minZ)..MathHelper.floor(around.maxZ)) {
				val state = world.getBlockState(cell.set(x, y, z))
				val live = BlockPhysicsCapture.capture(state, world, cell, context, ignoreHazards)
				val captured = snapshot.installedBlock(x, y, z)
				if (captured == live) continue
				differences += "($x,$y,$z) live ${state.block.translationKey.substringAfterLast('.')} ${live.collisionShape.boundingBoxes.map { it.short() }}" +
						" snapshot ${captured?.collisionShape?.boundingBoxes?.map { it.short() } ?: "unknown"}"
			}
		}
	}
	world.getOtherEntities(player, around) { it.isCollidable(player) }.forEach { entity ->
		differences += "collidable ${entity.type.untranslatedName} ${entity.boundingBox.short()}"
	}
	return if (differences.isEmpty()) "; snapshot matches the live world around the body"
	else "; snapshot differs from the live world in ${differences.size} cell(s): ${differences.take(MAX_REPORTED_DIFFERENCES).joinToString(", ")}"
}

private const val MAX_REPORTED_DIFFERENCES = 12

private const val MAX_REPORTED_ENTITIES = 4

private const val CONTACT_REACH = 0.5

private fun liveLedgeProbe(box: Box, movement: Vec3d): String {
	val player = mc.player ?: return ""
	val world = mc.world ?: return ""
	val step = player.stepHeight.toDouble()
	val probe = Box(
		box.minX + Vanilla.COLLISION_EPSILON + movement.x, box.minY - step - Vanilla.COLLISION_EPSILON, box.minZ + Vanilla.COLLISION_EPSILON + movement.z,
		box.maxX - Vanilla.COLLISION_EPSILON + movement.x, box.minY, box.maxZ - Vanilla.COLLISION_EPSILON + movement.z,
	)
	return "; live ledge probe %s dx=%.4f dz=%.4f step=%.2f worldEmpty=%s blocksEmpty=%s clipAtLedge=%s".format(
		probe.short(), movement.x, movement.z, step,
		world.isSpaceEmpty(player, probe),
		world.isBlockSpaceEmpty(player, probe),
		player.isSneaking,
	)
}

private fun Box.short() =
	"[%.3f,%.3f,%.3f -> %.3f,%.3f,%.3f]".format(minX, minY, minZ, maxX, maxY, maxZ)

private fun Vec3d.short() = "(%.4f,%.4f,%.4f)".format(x, y, z)
