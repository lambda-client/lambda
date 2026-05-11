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

package com.lambda.interaction.managers.hotbar

import com.lambda.context.Automated
import com.lambda.interaction.managers.Request

class HotbarRequest(
	val slot: Int,
	automated: Automated,
	var keepTicks: Int = automated.hotbarConfig.keepTicks,
	val swapPause: Int = automated.hotbarConfig.swapPause,
	override val nowOrNothing: Boolean = true
) : Request(), Automated by automated {
	override val requestId = ++requestCount
	override val tickStageMask get() = hotbarConfig.tickStageMask

	var activeRequestAge = 0
	var swapPauseAge = 0

	override val done: Boolean
		get() = slot == HotbarManager.activeSlot && swapPauseAge >= swapPause

	override fun submit(queueIfMismatchedStage: Boolean) =
		HotbarManager.request(this, queueIfMismatchedStage)

	companion object {
		var requestCount = 0
	}
}
