
package com.minato.interaction.handlers.packet

import com.minato.config.blocks.BuildConfig
import com.minato.context.Automated
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
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
	Interaction({ interactionLimit }),
	Inventory({ inventoryLimit })
}