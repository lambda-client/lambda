/*
 * Copyright 2026 Lambda
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

package com.lambda.pathing.execution

import com.lambda.util.world.FastVector
import com.lambda.util.world.toBlockPos
import net.minecraft.util.math.Vec3d

/**
 * Continuous execution-space pose derived from a discrete planner node.
 *
 * The planner still operates on block-foot nodes. The executor converts those
 * nodes into feet-center positions so segment following can work in continuous
 * space without changing the global D* Lite state space.
 */
data class ExecutionPose(
    val node: FastVector,
    val position: Vec3d = Vec3d.ofBottomCenter(node.toBlockPos()),
)
