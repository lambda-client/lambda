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
	var keepTicks: Int = automated.hotbarConfig.keepTicks,
	val swapPause: Int = automated.hotbarConfig.swapPause,
	override val nowOrNothing: Boolean = true,
	automated: Automated
) : Request(), Automated by automated {
	override val requestId = ++requestCount
	override val tickStageMask get() = hotbarConfig.tickStageMask

	var activeRequestAge = 0
	var swapPauseAge = 0

	override val done: Boolean
		get() = slot == HotbarManager.activeSlot && swapPauseAge >= swapPause

	@HotbarRequestMarker
	override fun submit(queueIfMismatchedStage: Boolean) =
		HotbarManager.request(this, queueIfMismatchedStage)

	companion object {
		var requestCount = 0
			private set
	}
}

@DslMarker
private annotation class HotbarRequestMarker

@Suppress("unused")
@HotbarRequestMarker
class HotbarRequestBuilder private constructor(
	private val slot: Int,
	private val nowOrNothing: Boolean,
	private val automated: Automated,
) {
	private var keepTicks: Int = automated.hotbarConfig.keepTicks
	private var swapPause: Int = automated.hotbarConfig.swapPause

	fun keepTicks(keepTicks: Int) {
		this.keepTicks = keepTicks
	}

	fun swapPause(swapPause: Int) {
		this.swapPause = swapPause
	}

	private fun build() = HotbarRequest(slot, keepTicks, swapPause, nowOrNothing, automated)

	companion object {
		fun Automated.hotbarRequest(slot: Int, nowOrNothing: Boolean = false, builder: (HotbarRequestBuilder.() -> Unit)? = null) =
			HotbarRequestBuilder(slot, nowOrNothing, this).apply { builder?.invoke(this) }.build()
	}
}