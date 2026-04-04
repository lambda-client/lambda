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

package com.lambda.command.commands

import com.lambda.brigadier.execute
import com.lambda.command.LambdaCommand
import com.lambda.threading.runSafe
import com.lambda.util.extension.CommandBuilder
import com.lambda.util.world.fastEntitySearch
import net.minecraft.entity.passive.AnimalEntity
import net.minecraft.util.Hand
import net.minecraft.util.hit.EntityHitResult

object AutoBreedCommand : LambdaCommand(
	name = "breed",
	usage = "breed",
	description = "Automatically interacts with animals around you to breed them"
) {
	override fun CommandBuilder.create() {
		execute {
			runSafe {
				fastEntitySearch<AnimalEntity>(5.0)
					.filter { it.isAlive }
					.forEach {
						interaction.interactEntityAtLocation(player, it, EntityHitResult(it), Hand.MAIN_HAND)
						interaction.interactEntity(player, it, Hand.MAIN_HAND)
						player.swingHand(Hand.MAIN_HAND)
					}
			}
		}
	}
}