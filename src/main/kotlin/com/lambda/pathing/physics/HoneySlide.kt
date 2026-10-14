package com.lambda.pathing.physics

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import kotlin.math.abs

fun SimulationEnvironment.besideHoney(state: MovementSimulationState): Boolean {
	if (state.onGround) return false
	val box = state.boundingBox.contract(Vanilla.BUBBLE_CONTRACTION.toDouble())
	val x0 = MathHelper.floor(box.minX)
	val y0 = MathHelper.floor(box.minY)
	val z0 = MathHelper.floor(box.minZ)
	val x1 = MathHelper.floor(box.maxX)
	val y1 = MathHelper.floor(box.maxY)
	val z1 = MathHelper.floor(box.maxZ)
	if (!mayContainHoney(x0, y0, z0, x1, y1, z1)) return false
	val reach = Vanilla.HONEY_SIDE_REACH + (box.lengthX + 2 * Vanilla.BUBBLE_CONTRACTION) / 2.0
	val cell = BlockPos.Mutable()
	for (y in y0..y1) for (x in x0..x1) for (z in z0..z1) {
		if (state.position.y > y + Vanilla.HONEY_SLIDE_TOP - Vanilla.HONEY_SLIDE_EPSILON) continue
		if (abs(x + 0.5 - state.position.x) + Vanilla.HONEY_SLIDE_EPSILON <= reach && abs(z + 0.5 - state.position.z) + Vanilla.HONEY_SLIDE_EPSILON <= reach) continue
		if (honey(cell.set(x, y, z))) return true
	}
	return false
}
