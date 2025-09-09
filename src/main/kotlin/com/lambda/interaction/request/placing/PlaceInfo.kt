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

package com.lambda.interaction.request.placing

import com.lambda.interaction.construction.context.BuildContext
import com.lambda.interaction.construction.context.PlaceContext
import com.lambda.interaction.request.ActionInfo
import com.lambda.interaction.request.LogContext
import com.lambda.interaction.request.LogContext.Companion.buildLogContext
import net.minecraft.util.math.BlockPos

data class PlaceInfo(
    override val context: PlaceContext,
    override val pendingInteractionsList: MutableCollection<BuildContext>,
    val onPlace: ((BlockPos) -> Unit)?,
    val placeConfig: PlaceConfig
) : ActionInfo, LogContext {
    override fun toLogContext() =
        buildLogContext {
            text("Place Info:")
            pushTab()
            text(context.toLogContext())
            text("Callbacks:")
            pushTab()
            value("onPlace", onPlace != null)
        }
}