
package com.minato.command.commands

import com.minato.brigadier.execute
import com.minato.command.MinatoCommand
import com.minato.threading.runSafe
import com.minato.util.extension.CommandBuilder
import com.minato.util.world.fastEntitySearch
import net.minecraft.entity.passive.AnimalEntity
import net.minecraft.util.Hand
import net.minecraft.util.hit.EntityHitResult

object BreedCommand : MinatoCommand(
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