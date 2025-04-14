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

package com.lambda.interaction.request.hotbar

data class SlotInfo(
    val slot: Int,
    var keepTicks: Int,
    var swapPause: Int
) {
    var activeRequestAge = 0
    var swapPauseAge = 0

    val swapPaused get() = swapPauseAge < swapPause
    val swappedThisTick get() = activeRequestAge <= 0
    val keeping get() = keepTicks > 0
}