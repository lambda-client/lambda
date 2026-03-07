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

package com.lambda.graphics.outline

import java.awt.Color

data class OutlineStyle(
    val color: Color,
    val thickness: Float = 0.0005f,
    val glowIntensity: Float = 0.5f,
    val glowRadius: Float = 0.001f,
    val fill: Boolean = true,
    val fillOpacity: Float = 0.4f
) {
    companion object {
        val DEFAULT = OutlineStyle(Color.WHITE)
    }
}
