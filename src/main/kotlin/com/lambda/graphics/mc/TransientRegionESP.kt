/*
 * Copyright 2025 Lambda
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

package com.lambda.graphics.mc

import com.lambda.graphics.esp.RegionESP
import com.lambda.graphics.esp.ShapeScope
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

/**
 * Modern replacement for the legacy Treed system. Handles geometry that is cleared and rebuilt
 * every tick. Uses region-based rendering for precision.
 */
class TransientRegionESP(name: String, depthTest: Boolean) : RegionESP(name, depthTest) {
	private val builders = ConcurrentHashMap<Long, ShapeScope>()

	/** Get or create a builder for a specific region. */
	override fun shapes(x: Double, y: Double, z: Double, block: ShapeScope.() -> Unit) {
		val key = getRegionKey(x, y, z)
		val scope =
			builders.getOrPut(key) {
				val size = RenderRegion.REGION_SIZE
				val rx = (size * floor(x / size)).toInt()
				val ry = (size * floor(y / size)).toInt()
				val rz = (size * floor(z / size)).toInt()
				ShapeScope(RenderRegion(rx, ry, rz))
			}
		scope.apply(block)
	}

	/** Clear all current builders. Call this at the end of every tick. */
	override fun clear() {
		builders.clear()
	}

	/** Upload collected geometry to GPU. Must be called on main thread. */
	override fun upload() {
		val activeKeys = builders.keys().asSequence().toSet()

		builders.forEach { (key, scope) ->
			val renderer = renderers.getOrPut(key) { RegionRenderer(scope.region) }
			renderer.upload(scope.builder.collector)
		}

		renderers.forEach { (key, renderer) ->
			if (key !in activeKeys) {
				renderer.clearData()
			}
		}
	}
}
