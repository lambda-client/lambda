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

package com.lambda.interaction.managers.interacting

import com.lambda.config.blocks.InteractConfig
import com.lambda.context.SafeContext
import com.lambda.interaction.construction.simulation.context.BuildContext
import com.lambda.interaction.construction.simulation.context.InteractContext
import com.lambda.interaction.managers.ActionInfo
import net.minecraft.util.math.BlockPos

data class InteractInfo(
	override val context: InteractContext,
	override val pendingInteractionsList: MutableCollection<BuildContext>,
	val onPlace: (SafeContext.(BlockPos) -> Unit)?,
	val interactConfig: InteractConfig
) : ActionInfo