package com.lambda.pathing.coarse

import com.lambda.pathing.core.Stance
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap

class FieldLabels internal constructor(private val labels: Long2DoubleOpenHashMap) {
	fun label(stance: Stance, speed: SpeedClass): Double = labels.get(PackedStance.pack(stance, speed))

	companion object {
		/** One leg's labels over several of its frames, later frames winning: each frame labels only its own route's band. */
		fun union(all: List<FieldLabels>): FieldLabels {
			if (all.size == 1) return all.first()
			val merged = Long2DoubleOpenHashMap(all.maxOf { it.labels.size })
			merged.defaultReturnValue(all.first().labels.defaultReturnValue())
			for (frame in all) merged.putAll(frame.labels)
			return FieldLabels(merged)
		}
	}
}
