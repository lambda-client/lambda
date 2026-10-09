package com.lambda.pathing.coarse

import com.lambda.pathing.core.Stance
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap

class FieldLabels internal constructor(private val labels: Long2DoubleOpenHashMap) {
	fun label(stance: Stance, speed: SpeedClass): Double = labels.get(PackedStance.pack(stance, speed))
}
