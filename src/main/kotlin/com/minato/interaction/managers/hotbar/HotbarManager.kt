
package com.minato.interaction.managers.hotbar

import com.minato.config.blocks.HotbarConfig.SwapMode
import com.minato.context.AutomatedSafeContext
import com.minato.context.SafeContext
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.interaction.managers.Manager
import com.minato.interaction.managers.hotbar.HotbarManager.activeRequest
import com.minato.interaction.managers.hotbar.HotbarManager.activeSlot
import com.minato.interaction.managers.hotbar.HotbarManager.checkResetSwap
import com.minato.interaction.managers.hotbar.HotbarManager.setActiveRequest
import com.minato.interaction.managers.hotbar.HotbarManager.setActiveSlot
import com.minato.threading.runSafe
import net.minecraft.item.ItemStack

/**
 * Manager responsible for handling the current selected hotbar index. It can be accessed from anywhere through a
 * [HotbarRequest]
 *
 * "Silent swapping" is a feature of this manager. If requested with a [HotbarRequest] that has [HotbarRequest.keepTicks]
 * set to 0, assuming the request is accepted, the manager will only swap for the duration of the current [tickStage].
 * After which, the manager will end the request and swap back to the player's selected slot.
 */
object HotbarManager : Manager<HotbarRequest>(
	1,
	onOpen = {
		if (activeRequest != null) {
			setActiveSlot()
		}
			 },
	onClose = { checkResetSwap() }
) {
	private var activeRequest: HotbarRequest? = null
	@JvmStatic var activeSlot: Int = -1

	val serverSlot get() = runSafe {
		interaction.lastSelectedSlot
	} ?: -1
	//ToDo: something to manage stacks so the hotbar manager is strictly index based
	private var previousStack: ItemStack? = null
	private var swappedTicks = 0

	private var swapsThisTick = 0
	private var maxSwapsThisTick = 0
	private var swapDelay = 0

	override fun load(): String {
		super.load()

        listen<TickEvent.Post>({ Int.MIN_VALUE }) {
            swapsThisTick = 0
            if (swapDelay > 0) swapDelay--

			val currentStack = player.mainHandStack
			if (previousStack != currentStack) swappedTicks = 1
			else swappedTicks++
			previousStack = currentStack

			val activeInfo = activeRequest ?: return@listen
			if (activeInfo.slot != activeSlot) return@listen
			activeInfo.swapPauseAge = swappedTicks
			activeInfo.activeRequestAge++
			activeInfo.keepTicks--
		}

		return "Loaded Hotbar Manager"
	}

	/**
	 * If the [activeRequest] is not null, being kept, and the [request]'s slot matches the [activeSlot], the
	 * [request]'s swapPauseAge is set to the swapped ticks and the request is denied. Otherwise, the active
	 * request is set and it attempts to set the active slot.
	 *
	 * @see setActiveRequest
	 * @see setActiveSlot
	 */
	override fun AutomatedSafeContext.handleRequest(request: HotbarRequest) {
		if (request.slot !in 0..8) return
		if (request.nowOrNothing && tickStage !in hotbarConfig.tickStageMask) return

		activeRequest?.let { active ->
			if (active.activeRequestAge <= 0 && active.keepTicks > 0) {
				if (activeSlot == request.slot) request.swapPauseAge = swappedTicks
				return
			}
		}

		setActiveRequest(request)
		if (!setActiveSlot() && request.nowOrNothing) {
			activeRequest = null
			activeSlot = -1
		}
	}

	private fun AutomatedSafeContext.setActiveRequest(request: HotbarRequest) {
		maxSwapsThisTick = hotbarConfig.swapsPerTick
		activeRequest = request
	}

	/**
	 * Sets the [activeSlot]. This also calls syncSelectedSlot to
	 * update the server to keep predictability.
	 *
	 * @see net.minecraft.client.network.ClientPlayerInteractionManager.syncSelectedSlot
	 */
	private fun SafeContext.setActiveSlot(): Boolean {
		activeRequest?.let { activeRequest ->
			if (serverSlot != activeRequest.slot) {
				if (tickStage !in activeRequest.hotbarConfig.tickStageMask) return false
				if (swapsThisTick + 1 > maxSwapsThisTick || swapDelay > 0) return false
				swapsThisTick++
				swappedTicks = 0
				swapDelay = activeRequest.hotbarConfig.swapDelay
			} else activeRequest.swapPauseAge = swappedTicks
			if (activeRequest.slot == activeSlot) return true
			activeSlot = activeRequest.slot
			if (activeRequest.hotbarConfig.swapMode == SwapMode.Permanent)
				player.inventory.selectedSlot = activeRequest.slot
			interaction.syncSelectedSlot()
		}
		return true
	}

	/**
	 * Called after every [tickStage] closes. This method checks if the current [activeRequest] should be stopped.
	 * This action is counted as another swap, so the conditions for a regular swap must be met. If the requests
	 * [com.minato.config.blocks.HotbarConfig.tickStageMask] does not contain the current tick stage, no actions can be performed.
	 *
	 * @see net.minecraft.client.network.ClientPlayerInteractionManager.syncSelectedSlot
	 */
	private fun SafeContext.checkResetSwap() {
		activeRequest?.let { active ->
			if (active.keepTicks <= 0) {
				if (active.hotbarConfig.swapMode == SwapMode.Permanent) {
					activeRequest = null
					activeSlot = -1
					return
				}
				val canStopSwap = swapsThisTick < maxSwapsThisTick
				if (tickStage in active.hotbarConfig.tickStageMask && canStopSwap) {
					val prevSlot = activeSlot
					activeRequest = null
					activeSlot = -1
					interaction.syncSelectedSlot()
					if (serverSlot != prevSlot) swapsThisTick++
				}
			}
		}
	}
}
