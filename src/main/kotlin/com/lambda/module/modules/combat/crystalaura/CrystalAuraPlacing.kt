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

package com.lambda.module.modules.combat.crystalaura

import com.lambda.threading.runSafe
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult

object CrystalAuraPlacing {
	fun CrystalAura.placeInternal(opportunity: Opportunity, hand: Hand) = runSafe {
		lastPlace = Pair(opportunity.blockPos, System.currentTimeMillis())
		interaction.syncSelectedSlot() // TODO: when server only hotbar swap gets implemented, this will be removed
		interaction.sendSequencedPacket(world) { sequence ->
			PlayerInteractBlockC2SPacket(
				hand, BlockHitResult(opportunity.crystalPosition, opportunity.side, opportunity.blockPos, false), sequence
			)
		}

		player.swingHand(hand)
		waitingForCrystal = true
	}
}