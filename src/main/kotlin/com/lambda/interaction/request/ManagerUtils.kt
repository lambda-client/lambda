/*
 * Copyright 2025 Lambda
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

package com.lambda.interaction.request

import com.lambda.event.Event
import com.lambda.util.reflections.getInstances
import net.minecraft.util.math.BlockPos

object ManagerUtils {
    val managers = getInstances<RequestHandler<*>>()
    val accumulatedManagerPriority = managers.map { it.stagePriority }.reduce { acc, priority -> acc + priority }
    val positionBlockingManagers = getInstances<PositionBlocking>()

    fun DebugLogger.newTick() =
        system("------------- New Tick -------------")

    fun DebugLogger.newStage(tickStage: Event?) =
        system("Tick stage ${tickStage?.run { this::class.qualifiedName }}")

    fun isPosBlocked(pos: BlockPos) =
        positionBlockingManagers.any { manager -> manager.blockedPositions.any { blocked -> blocked == pos } }
}