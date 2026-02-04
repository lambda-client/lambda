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

import org.joml.Vector3f

/**
 * Configuration for GUI item lighting.
 * Controls the two-light shading model used for inventory-style rendering.
 *
 * @param light0 Primary light direction (normalized)
 * @param light1 Fill light direction (normalized)
 * @param ambient Base ambient lighting level (0.0-1.0)
 * @param strength0 Primary light contribution multiplier
 * @param strength1 Fill light contribution multiplier
 * @param respectsUseLight When true, respects the layer's useLight flag to skip shading on flat items.
 *                         When false, always applies the specified lighting regardless of item type.
 */
data class ItemLighting(
    val light0: Vector3f,
    val light1: Vector3f,
    val ambient: Float = 0.6f,
    val strength0: Float = 0.2f,
    val strength1: Float = 0.2f,
    val respectsUseLight: Boolean = false
) {
    companion object {
        /**
         * Vanilla inventory lighting for GUI items.
         * Respects the per-layer useLight flag to match vanilla behavior:
         * - 3D blocks (useLight=true): Apply diffuse shading
         * - Flat items (useLight=false): Skip shading, full brightness
         * 
         * Raw light values from DiffuseLighting.java:
         * - DEFAULT_DIFFUSION_LIGHT_0 = (0.2, 1.0, -0.7)
         * - DEFAULT_DIFFUSION_LIGHT_1 = (-0.2, 1.0, 0.7)
         */
        val VANILLA: ItemLighting by lazy {
            val light0 = Vector3f(0.2f, 1.0f, -0.7f).normalize()
            val light1 = Vector3f(-0.2f, 1.0f, 0.7f).normalize()
            ItemLighting(light0, light1, respectsUseLight = true)
        }
        
        /**
         * No shading - produces a flat, evenly lit look.
         * Ignores the layer's useLight flag.
         */
        val NONE = ItemLighting(
            light0 = Vector3f(0f, 1f, 0f),
            light1 = Vector3f(0f, 1f, 0f),
            ambient = 1.0f,
            strength0 = 0f,
            strength1 = 0f,
            respectsUseLight = false
        )
        
        /**
         * Creates lighting with a single directional light.
         * Ignores the layer's useLight flag - always applies the specified lighting.
         * @param direction The light direction (will be normalized)
         * @param strength How strong the directional component is (0.0-1.0)
         */
        fun directional(direction: Vector3f, strength: Float = 0.4f): ItemLighting {
            val normalized = Vector3f(direction).normalize()
            return ItemLighting(
                light0 = normalized,
                light1 = Vector3f(-normalized.x, normalized.y, -normalized.z).normalize(),
                ambient = 1.0f - strength,
                strength0 = strength * 0.7f,
                strength1 = strength * 0.3f,
                respectsUseLight = false
            )
        }
    }
}
