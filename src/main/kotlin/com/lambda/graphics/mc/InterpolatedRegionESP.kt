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
 * Interpolated region-based ESP system for smooth entity rendering.
 *
 * Unlike TransientRegionESP which rebuilds every tick, this system stores both previous and current
 * frame data and interpolates between them during rendering for smooth movement at any framerate.
 */
class InterpolatedRegionESP(name: String, depthTest: Boolean = false) : RegionESP(name, depthTest) {
	// Current frame builders (being populated this tick)
	private val currBuilders = ConcurrentHashMap<Long, ShapeScope>()

	// Previous frame data (uploaded last tick)
	private val prevBuilders = ConcurrentHashMap<Long, ShapeScope>()

	// Interpolated collectors for rendering (computed each frame)
	private val interpolatedCollectors =
		ConcurrentHashMap<Long, RegionVertexCollector>()

	// Track if we need to re-interpolate
	private var lastTickDelta = -1f
	private var needsInterpolation = true

	override fun shapes(x: Double, y: Double, z: Double, block: ShapeScope.() -> Unit) {
		val key = getRegionKey(x, y, z)
		val scope =
			currBuilders.getOrPut(key) {
				val size = RenderRegion.REGION_SIZE
				val rx = (size * floor(x / size)).toInt()
				val ry = (size * floor(y / size)).toInt()
				val rz = (size * floor(z / size)).toInt()
				ShapeScope(RenderRegion(rx, ry, rz), collectShapes = true)
			}
		scope.apply(block)
	}

	override fun clear() {
		prevBuilders.clear()
		currBuilders.clear()
		interpolatedCollectors.clear()
	}

	fun tick() {
		prevBuilders.clear()
		prevBuilders.putAll(currBuilders)
		currBuilders.clear()
		needsInterpolation = true
	}

	override fun upload() {
		needsInterpolation = true
	}

	override fun render(tickDelta: Float) {
		if (needsInterpolation || lastTickDelta != tickDelta) {
			interpolate(tickDelta)
			uploadInterpolated()
			lastTickDelta = tickDelta
			needsInterpolation = false
		}
		super.render(tickDelta)
	}

	private fun interpolate(tickDelta: Float) {
		interpolatedCollectors.clear()
		(prevBuilders.keys + currBuilders.keys).toSet().forEach { key ->
			val prevScope = prevBuilders[key]
			val currScope = currBuilders[key]
			val collector = RegionVertexCollector()
			val region = currScope?.region ?: prevScope?.region ?: return@forEach

			val prevShapes = prevScope?.shapes?.associateBy { it.id } ?: emptyMap()
			val currShapes = currScope?.shapes?.associateBy { it.id } ?: emptyMap()

			val allIds = (prevShapes.keys + currShapes.keys).toSet()

			for (id in allIds) {
				val prev = prevShapes[id]
				val curr = currShapes[id]

				when {
					prev != null && curr != null -> {
						curr.renderInterpolated(prev, tickDelta, collector, region)
					}
					curr != null -> {
						// New shape - just render
						curr.renderInterpolated(curr, 1.0f, collector, region)
					}
					prev != null -> {
						// Disappeared - render at previous position
						prev.renderInterpolated(prev, 1.0f, collector, region)
					}
				}
			}

			if (collector.faceVertices.isNotEmpty() || collector.edgeVertices.isNotEmpty()) {
				interpolatedCollectors[key] = collector
			}
		}
	}

	private fun uploadInterpolated() {
		val activeKeys = interpolatedCollectors.keys.toSet()
		interpolatedCollectors.forEach { (key, collector) ->
			val region = currBuilders[key]?.region ?: prevBuilders[key]?.region ?: return@forEach

			val renderer = renderers.getOrPut(key) { RegionRenderer(region) }
			renderer.upload(collector)
		}

		renderers.forEach { (key, renderer) ->
			if (key !in activeKeys) {
				renderer.clearData()
			}
		}
	}
}
