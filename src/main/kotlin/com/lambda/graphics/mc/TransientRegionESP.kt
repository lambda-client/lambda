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

import com.lambda.Lambda.mc
import com.mojang.blaze3d.systems.RenderSystem
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

/**
 * Modern replacement for the legacy Treed system. Handles geometry that is cleared and rebuilt
 * every tick. Uses region-based rendering for precision.
 */
class TransientRegionESP(val name: String) {
	private val renderers = ConcurrentHashMap<Long, RegionRenderer>()
	private val builders = ConcurrentHashMap<Long, RegionShapeBuilder>()

	/** Get or create a builder for a specific region. */
	fun getBuilder(x: Double, y: Double, z: Double): RegionShapeBuilder {
		val size = RenderRegion.REGION_SIZE
		val rx = (size * floor(x / size)).toInt()
		val ry = (size * floor(y / size)).toInt()
		val rz = (size * floor(z / size)).toInt()

		val key = (rx.toLong() and 0xFFFFFFFFL) or ((rz.toLong() and 0xFFFFFFFFL) shl 32)

		return builders.getOrPut(key) { RegionShapeBuilder(RenderRegion(rx, ry, rz)) }
	}

	/** Clear all current builders. Call this at the end of every tick. */
	fun clear() {
		builders.clear()
	}

	/** Upload collected geometry to GPU. Must be called on main thread. */
	fun upload() {
		val activeKeys = builders.keys().asSequence().toSet()

		builders.forEach { (key, builder) ->
			val renderer = renderers.getOrPut(key) { RegionRenderer(builder.region) }
			renderer.upload(builder.collector)
		}

		renderers.forEach { (key, renderer) ->
			if (key !in activeKeys) {
				renderer.clearData()
			}
		}
	}

	/** Render all active regions. */
	fun render(throughWalls: Boolean = false) {
		val camera = mc.gameRenderer?.camera ?: return
		val cameraPos = camera.pos

		val activeRenderers = renderers.values.filter { it.hasData() }
		if (activeRenderers.isEmpty()) return

		val transforms =
			activeRenderers.map { renderer ->
				val offset = renderer.region.computeCameraRelativeOffset(cameraPos)
				val rotation = camera.rotation.conjugate(Quaternionf())
				val modelView = Matrix4f().rotation(rotation).translate(offset)

				val dynamicTransform =
					RenderSystem.getDynamicUniforms()
						.write(
							modelView,
							Vector4f(1f, 1f, 1f, 1f),
							Vector3f(0f, 0f, 0f),
							Matrix4f()
						)
				renderer to dynamicTransform
			}

		val facePass = RegionRenderer.createRenderPass("Transient ESP Faces ($name)") ?: return
		facePass.use { pass ->
			val pipeline =
				if (throughWalls) LambdaRenderPipelines.ESP_QUADS_THROUGH
				else LambdaRenderPipelines.ESP_QUADS
			pass.setPipeline(pipeline)
			RenderSystem.bindDefaultUniforms(pass)
			transforms.forEach { (renderer, transform) ->
				pass.setUniform("DynamicTransforms", transform)
				renderer.renderFaces(pass)
			}
		}

		val edgePass = RegionRenderer.createRenderPass("Transient ESP Edges ($name)") ?: return
		edgePass.use { pass ->
			val pipeline =
				if (throughWalls) LambdaRenderPipelines.ESP_LINES_THROUGH
				else LambdaRenderPipelines.ESP_LINES
			pass.setPipeline(pipeline)
			RenderSystem.bindDefaultUniforms(pass)
			transforms.forEach { (renderer, transform) ->
				pass.setUniform("DynamicTransforms", transform)
				renderer.renderEdges(pass)
			}
		}
	}

	fun close() {
		renderers.values.forEach { it.close() }
		renderers.clear()
		builders.clear()
	}
}
