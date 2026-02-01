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

package com.lambda.util.extension

import com.lambda.interaction.construction.verify.TargetState
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.command.CommandSource
import net.minecraft.util.math.BlockPos

typealias CommandBuilder = LiteralArgumentBuilder<CommandSource>
typealias Structure = Map<BlockPos, TargetState>

fun Structure.move(offset: BlockPos): Structure =
	mapKeys { (pos, _) -> pos.add(offset) }

fun Structure.moveX(x: Int): Structure =
	mapKeys { (pos, _) -> pos.east(x) }

fun Structure.moveY(y: Int): Structure =
	mapKeys { (pos, _) -> pos.up(y) }

fun Structure.moveZ(z: Int): Structure =
	mapKeys { (pos, _) -> pos.south(z) }
