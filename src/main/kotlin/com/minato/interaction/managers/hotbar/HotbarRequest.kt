
package com.minato.interaction.managers.hotbar

import com.minato.context.Automated
import com.minato.interaction.managers.Request

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
