
package com.minato.interaction.managers

import com.minato.util.ReflectionUtils.getInstances
import net.minecraft.util.math.BlockPos

object ManagerUtils {
	val managers = getInstances<Manager<*>>()
	val accumulatedManagerPriority = managers.map { it.stagePriority }.reduce { acc, priority -> acc + priority }
	val positionBlockingManagers = getInstances<PositionBlocking>()

	fun isPosBlocked(pos: BlockPos) =
		positionBlockingManagers.any { pos in it.blockedPositions }
}