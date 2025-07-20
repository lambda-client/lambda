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

package com.lambda.gui.component

enum class HAlign(val multiplier: Double, val offset: Double) {
    LEFT(0.0, -1.0),
    CENTER(0.5, 0.0),
    RIGHT(1.0, 1.0)
}

enum class VAlign(val multiplier: Double, val offset: Double) {
    TOP(0.0, -1.0),
    CENTER(0.5, 0.0),
    BOTTOM(1.0, 1.0)
}
