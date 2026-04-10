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

object PacketLimitHandler {
	private val packetLimitMap = PacketType.entries.associateWith { LimitHandler() }

	init {
		listen<TickEvent.Pre>(priority = { Int.MAX_VALUE }) {
			val currentTime = System.currentTimeMillis()
			packetLimitMap.values.forEach { it.removeStale(currentTime) }
		}
	}

	context(automated: Automated)
	fun canSendPackets(packetCount: Int, packetType: PacketType): Boolean {
		val handler = packetLimitMap[packetType] ?: return false
		handler.maxPacketsThisTimeframe = packetType.maxPacketsPerTimeframe(automated.buildConfig)
		handler.timeframeMillis = automated.buildConfig.limitTimeframe
		handler.removeStale(System.currentTimeMillis())
		return handler.packetTimestamps.size + packetCount <= handler.maxPacketsThisTimeframe
	}

	fun sentPackets(packetCount: Int, packetType: PacketType) {
		packetLimitMap[packetType]?.let { handler ->
			val currentTime = System.currentTimeMillis()
			repeat((0 until packetCount).count()) {
				handler.packetTimestamps.addLast(currentTime)
			}
		}
	}

	private class LimitHandler {
		var maxPacketsThisTimeframe = 0
		var timeframeMillis = 0
		val packetTimestamps = ArrayDeque<Long>()

		fun removeStale(currentTime: Long) {
			if (timeframeMillis <= 0) return
			while (packetTimestamps.isNotEmpty() && packetTimestamps.first() <= currentTime - timeframeMillis) {
				packetTimestamps.removeFirst()
			}
		}
	}
}

enum class PacketType(val maxPacketsPerTimeframe: BuildConfig.() -> Int) {
	PlayerAction({ actionLimit }),
	Interaction({ interactionPacketLimit })
}