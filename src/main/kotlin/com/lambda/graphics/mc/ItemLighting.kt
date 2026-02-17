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

data class ItemLighting(
    val light0: Vector3f,
    val light1: Vector3f,
    val ambient: Float = 0.6f,
    val strength0: Float = 0.2f,
    val strength1: Float = 0.2f,
    val respectsUseLight: Boolean = false
) {
    companion object {
        val VANILLA: ItemLighting by lazy {
            val light0 = Vector3f(0.2f, 1.0f, -0.7f).normalize()
            val light1 = Vector3f(-0.2f, 1.0f, 0.7f).normalize()
            ItemLighting(light0, light1, respectsUseLight = true)
        }
        
        val NONE = ItemLighting(
            light0 = Vector3f(0f, 1f, 0f),
            light1 = Vector3f(0f, 1f, 0f),
            ambient = 1.0f,
            strength0 = 0f,
            strength1 = 0f,
            respectsUseLight = false
        )
        
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
