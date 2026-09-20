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
import com.lambda.util.PacketUtils.sendPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.util.Hand

object CrystalAuraExploding {
	fun CrystalAura.explodeInternal(id: Int) = runSafe {
		connection.sendPacket {
			PlayerInteractEntityC2SPacket(
				id, player.isSneaking, PlayerInteractEntityC2SPacket.ATTACK
			)
		}

		player.swingHand(Hand.MAIN_HAND)
	}
}