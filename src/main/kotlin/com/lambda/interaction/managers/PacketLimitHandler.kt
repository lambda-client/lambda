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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ComparableTimeMark
import kotlin.time.TimeSource

object PacketLimitHandler {
	private val packetLimitMap = PacketType.entries.associateWith { LimitHandler() }

	init {
		listen<TickEvent.Pre>(priority = { Int.MAX_VALUE }) {
			val currentTime = TimeSource.Monotonic.markNow()
			packetLimitMap.values.forEach { it.removeStale(currentTime) }
		}
	}

	context(automated: Automated)
	fun canSendPackets(packetCount: Int, packetType: PacketType): Boolean {
		val handler = packetLimitMap[packetType] ?: return false
		handler.maxPacketsThisTimeframe = packetType.maxPacketsPerTimeframe(automated.buildConfig)
		handler.timeframe = automated.buildConfig.limitTimeframe.milliseconds
		handler.removeStale(TimeSource.Monotonic.markNow())
		return handler.packetTimestamps.size + packetCount <= handler.maxPacketsThisTimeframe
	}

	fun sentPackets(packetCount: Int, packetType: PacketType) {
		packetLimitMap[packetType]?.let { handler ->
			val currentTime = TimeSource.Monotonic.markNow()
			repeat((0 until packetCount).count()) {
				handler.packetTimestamps.addLast(currentTime)
			}
		}
	}

	private class LimitHandler {
		var maxPacketsThisTimeframe = 0
		var timeframe: Duration = Duration.ZERO
		val packetTimestamps = ArrayDeque<ComparableTimeMark>()

		fun removeStale(currentTime: ComparableTimeMark) {
			if (timeframe <= Duration.ZERO) return
			while (packetTimestamps.isNotEmpty() && packetTimestamps.first() <= currentTime - timeframe) {
				packetTimestamps.removeFirst()
			}
		}
	}
}

enum class PacketType(val maxPacketsPerTimeframe: BuildConfig.() -> Int) {
	PlayerAction({ actionLimit }),
	Interaction({ interactionLimit })
}