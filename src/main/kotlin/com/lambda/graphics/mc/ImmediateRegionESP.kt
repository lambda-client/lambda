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

package com.lambda.graphics.mc

import com.lambda.graphics.esp.RegionESP
import com.lambda.graphics.esp.ShapeScope
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

/**
 * Interpolated region-based ESP system for smooth entity rendering.
 *
 * This system rebuilds and uploads vertices every frame. Callers are responsible for providing
 * interpolated positions (e.g., using entity.prevX/x with tickDelta). The tick() method swaps
 * builders to allow smooth transitions between frames.
 */
class ImmediateRegionESP(name: String, depthTest: Boolean = false) : RegionESP(name, depthTest) {
	// Current frame builders (being populated this tick)
	private val currBuilders = ConcurrentHashMap<Long, ShapeScope>()

	override fun shapes(x: Double, y: Double, z: Double, block: ShapeScope.() -> Unit) {
		val key = getRegionKey(x, y, z)
		val scope =
			currBuilders.getOrPut(key) {
				val size = RenderRegion.REGION_SIZE
				val rx = (size * floor(x / size)).toInt()
				val ry = (size * floor(y / size)).toInt()
				val rz = (size * floor(z / size)).toInt()
				ShapeScope(RenderRegion(rx, ry, rz))
			}
		scope.apply(block)
	}

	override fun clear() {
		currBuilders.clear()
	}

	fun tick() {
		currBuilders.clear()
	}

	override fun upload() {
		val activeKeys = currBuilders.keys.toSet()

		currBuilders.forEach { (key, scope) ->
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
