
package com.minato.interaction.managers.breaking

import com.minato.config.blocks.BreakConfig
import com.minato.context.SafeContext
import com.minato.interaction.construction.simulation.context.BreakContext
import com.minato.interaction.handlers.breaking.RebreakHandler
import com.minato.interaction.managers.ActionInfo
import com.minato.interaction.managers.breaking.BreakInfo.BreakType.Primary
import com.minato.interaction.managers.breaking.BreakInfo.BreakType.Rebreak
import com.minato.interaction.managers.breaking.BreakInfo.BreakType.RedundantSecondary
import com.minato.interaction.managers.breaking.BreakInfo.BreakType.Secondary
import com.minato.interaction.managers.breaking.BreakManager.calcBreakDelta
import com.minato.threading.runSafeAutomated
import com.minato.util.Describable
import com.minato.util.NamedEnum
import net.minecraft.entity.ItemEntity
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action

/**
 * A data class that holds all the information required to process and continue a break.
 */
data class BreakInfo(
	override var context: BreakContext,
	var type: BreakType,
	var request: BreakRequest
) : ActionInfo {
	// Delegates
	val breakConfig get() = request.breakConfig
	override val pendingInteractionsList get() = request.pendingInteractions

	// Pre Processing
	var shouldProgress = false
	var rebreakPotential = RebreakHandler.RebreakPotential.None
	var swapInfo = SwapInfo.EMPTY
	var swapStack: ItemStack = ItemStack.EMPTY

	// BreakInfo Specific
	var updatedThisTick = true
	var updatedPreProcessingThisTick = false
	var progressedThisTick = false

	// Processing
	var breaking = false
	var abandoned = false
	var breakingTicks = 0
	var soundsCooldown = 0f
	var vanillaInstantBreakable = false
	val rebreakable get() = !vanillaInstantBreakable && type == Primary

	enum class BreakType(
		override val displayName: String,
		override val description: String
	) : NamedEnum, Describable {
		Primary("Primary", "The main block you’re breaking right now."),
		Secondary("Secondary", "A second block broken at the same time (when double‑break is enabled)."),
		RedundantSecondary("Redundant Secondary", "A previously started secondary break that’s now ignored/monitored only (no new actions)."),
		Rebreak("Rebreak", "A previously broken block which new breaks in the same position can compound progression on. Often rebreaking instantly.");
	}

	// Post Processing
	var broken = false; private set
	private var item: ItemEntity? = null
	val callbacksCompleted
		get() = broken && (request.onItemDrop == null || item != null)

	context(safeContext: SafeContext)
	fun internalOnBreak() {
		if (type != Rebreak) broken = true
		item?.let { item ->
			request.onItemDrop?.invoke(safeContext, item)
		}
	}

	context(safeContext: SafeContext)
	fun internalOnItemDrop(item: ItemEntity) {
		if (type != Rebreak) this.item = item
		if (broken || type == Rebreak) {
			request.onItemDrop?.invoke(safeContext, item)
		}
	}

	fun updateInfo(context: BreakContext, request: BreakRequest? = null) {
		updatedThisTick = true
		this.context = context
		request?.let { this.request = it }
		if (type == RedundantSecondary) type = Secondary
	}

	fun resetCallbacks() {
		broken = false
		item = null
	}

	fun tickChecks() {
		updatedThisTick = false
		updatedPreProcessingThisTick = false
		progressedThisTick = false
	}

	context(safeContext: SafeContext)
	fun setBreakingTextureStage(
		stage: Int = getBreakTextureProgress()
	) = safeContext.world.setBlockBreakingInfo(safeContext.player.id, context.blockPos, stage)

	context(safeContext: SafeContext)
	private fun getBreakTextureProgress(): Int = with(safeContext) {
		val item =
			if (breakConfig.swapMode.isEnabled() && breakConfig.swapMode != BreakConfig.SwapMode.Start) swapStack
			else player.mainHandStack
		val breakDelta = request.runSafeAutomated { context.cachedState.calcBreakDelta(context.blockPos, item) }
		val progress = (breakDelta * breakingTicks) / (getBreakThreshold() + (breakDelta * breakConfig.fudgeFactor))
		return if (progress > 0.0f) (progress * 10.0f).toInt().coerceAtMost(9) else -1
	}

	fun getBreakThreshold() =
		when (type) {
			Primary,
			Rebreak-> breakConfig.breakThreshold
			else -> 1.0f
		}

	context(_: SafeContext)
	fun startBreakPacket() = breakPacket(Action.START_DESTROY_BLOCK)

	context(_: SafeContext)
	fun stopBreakPacket() = breakPacket(Action.STOP_DESTROY_BLOCK)

	context(_: SafeContext)
	fun abortBreakPacket() = breakPacket(Action.ABORT_DESTROY_BLOCK)

	context(safeContext: SafeContext)
	private fun breakPacket(action: Action) =
		with(safeContext) {
			interaction.sendSequencedPacket(world) { sequence: Int ->
				PlayerActionC2SPacket(
					action,
					context.blockPos,
					context.hitResult.side,
					sequence
				)
			}
		}

	override fun toString() = "$type, ${context.cachedState}, ${context.blockPos}"
}
