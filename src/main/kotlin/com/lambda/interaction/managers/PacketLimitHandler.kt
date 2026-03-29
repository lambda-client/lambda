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

package com.lambda.interaction.managers

import com.lambda.config.groups.BuildConfig
import com.lambda.context.Automated
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.util.TickTimer

object PacketLimitHandler {
	private val packetLimitMap = PacketType.entries.associateWith { LimitHandler(0, 0, 0) }

	init {
		listen<TickEvent.Pre>(priority = { Int.MIN_VALUE }) {
			packetLimitMap.values.forEach { limitHandler ->
				with(limitHandler) {
					tickTimer.tick()
					if (tickTimer.hasSurpassed(timeframe)) {
						packetsThisTimeframe = 0
						tickTimer.reset()
					}
				}
			}
		}
	}

	context(automated: Automated)
	fun canSendPackets(packetCount: Int, packetType: PacketType) =
		packetLimitMap[packetType]?.let {
			it.maxPacketsThisTimeframe = packetType.maxPacketsPerTimeframe(automated.buildConfig)
			it.timeframe = automated.buildConfig.limitTimeframe
			it.packetsThisTimeframe + packetCount <= it.maxPacketsThisTimeframe
		} ?: false

	fun sentPackets(packetCount: Int, packetType: PacketType) =
		packetLimitMap[packetType]?.let { limitHandler ->
			if (limitHandler.packetsThisTimeframe == 0) limitHandler.tickTimer.reset()
			limitHandler.packetsThisTimeframe += packetCount
		}

	private data class LimitHandler(var maxPacketsThisTimeframe: Int, var packetsThisTimeframe: Int, var timeframe: Int) {
		val tickTimer = TickTimer()
	}
}

enum class PacketType(val maxPacketsPerTimeframe: BuildConfig.() -> Int) {
	PlayerAction({ actionPacketLimit }),
	Interaction({ interactionPacketLimit })
}