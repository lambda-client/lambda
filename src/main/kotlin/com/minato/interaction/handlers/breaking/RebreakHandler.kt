
package com.minato.interaction.handlers.breaking

import com.minato.config.blocks.BreakConfig
import com.minato.context.SafeContext
import com.minato.event.events.ConnectionEvent
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.event.listener.UnsafeListener.Companion.listenUnsafe
import com.minato.interaction.construction.simulation.context.BreakContext
import com.minato.interaction.handlers.breaking.BrokenBlockHandler.destroyBlock
import com.minato.interaction.handlers.breaking.RebreakHandler.rebreak
import com.minato.interaction.handlers.packet.PacketLimitHandler
import com.minato.interaction.handlers.packet.PacketType
import com.minato.interaction.managers.breaking.BreakInfo
import com.minato.interaction.managers.breaking.BreakManager
import com.minato.interaction.managers.breaking.BreakManager.calcBreakDelta
import com.minato.interaction.managers.breaking.BreakRequest
import com.minato.threading.runSafeAutomated
import com.minato.util.player.PlayerUtils.swingHand
import net.minecraft.util.Hand

/**
 * Designed to track the latest primary-broken [com.minato.interaction.managers.breaking.BreakInfo] in order to exploit a flaw in Minecraft's code that allows
 * the user to break any block placed in said position using the progress from the previously broken block.
 */
object RebreakHandler {
	var rebreak: BreakInfo? = null

    init {
        listen<TickEvent.Post>({ Int.MIN_VALUE + 1 }) {
            rebreak?.run {
                if (!progressedThisTick) {
                    breakingTicks++
                    progressedThisTick = true
                }
            }
        }

        listenUnsafe<ConnectionEvent.Connect.Pre>({ Int.MIN_VALUE }) {
            rebreak = null
        }
    }

	/**
	 * Tests to see if the [BreakInfo] can be accepted. If not, nothing happens. Otherwise,
	 * the [rebreak] is set, and the [com.minato.interaction.managers.breaking.BreakRequest.onReBreakStart] callback is invoked.
	 */
	context(safeContext: SafeContext)
	fun offerRebreak(info: BreakInfo) {
		if (!info.rebreakable) return

		rebreak = info.apply {
			type = BreakInfo.BreakType.Rebreak
			breaking = true
			resetCallbacks()
		}
		info.request.onReBreakStart?.invoke(safeContext, info.context.blockPos)
	}

	fun clearRebreak() {
		rebreak = null
	}

	/**
	 * [RebreakPotential.None] if it cannot be rebroken at all.
	 *
	 * [RebreakPotential.PartialProgress] if some progress would be added to the break.
	 *
	 * [RebreakPotential.Instant] if the block can be instantly rebroken.
	 *
	 * @return In what way this block can be rebroken.
	 */
	context(_: SafeContext)
	fun BreakInfo.getRebreakPotential() = request.runSafeAutomated {
		rebreak?.let { reBreak ->
			val stack = if (breakConfig.swapMode.isEnabled())
				swapStack
			else player.mainHandStack
			val breakDelta = context.cachedState.calcBreakDelta(context.blockPos, stack)
			val possible = reBreak.breakConfig.rebreak &&
					context.blockPos == reBreak.context.blockPos
			val instant = (reBreak.breakingTicks - breakConfig.fudgeFactor) * breakDelta >= breakConfig.breakThreshold
			when {
				possible && instant -> RebreakPotential.Instant
				possible -> RebreakPotential.PartialProgress
				else -> RebreakPotential.None
			}
		} ?: RebreakPotential.None
	}

	/**
	 * Updates the current [rebreak] with a fresh [BreakContext], and attempts to rebreak the block if possible.
	 *
	 * @return A [RebreakResult] to indicate how the update has been processed.
	 */
	context(_: SafeContext)
	fun handleUpdate(ctx: BreakContext, request: BreakRequest) = request.runSafeAutomated {
		val reBreak = this@RebreakHandler.rebreak ?: return@runSafeAutomated RebreakResult.Ignored

		reBreak.updateInfo(ctx, request)

		val context = reBreak.context
		val breakDelta = context.cachedState.calcBreakDelta(context.blockPos)
		val breakTicks = reBreak.breakingTicks - breakConfig.fudgeFactor
		return@runSafeAutomated if (breakTicks * breakDelta >= reBreak.getBreakThreshold()) {
			if (!PacketLimitHandler.canSendPackets(1, PacketType.PlayerAction)) return@runSafeAutomated RebreakResult.Ignored
			if (breakConfig.breakConfirmation != BreakConfig.BreakConfirmationMode.AwaitThenBreak) {
				destroyBlock(reBreak)
			}
			reBreak.stopBreakPacket()
			PacketLimitHandler.sentPackets(1, PacketType.PlayerAction)
			if (breakConfig.swing.isEnabled()) {
				swingHand(breakConfig.swingType, Hand.MAIN_HAND)
			}
			BreakManager.breaksThisTick++
			RebreakResult.Rebroke
		} else {
			RebreakResult.StillBreaking(reBreak)
		}
	}

	enum class RebreakPotential {
		Instant,
		PartialProgress,
		None;

		fun isPossible() = this != None
	}
}