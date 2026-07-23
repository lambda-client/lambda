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

package com.lambda.interaction.managers.breaking

import com.lambda.config.blocks.BreakConfig
import com.lambda.config.blocks.BreakConfig.BreakConfirmationMode
import com.lambda.config.blocks.BreakConfig.BreakMode
import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.EntityEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer
import com.lambda.interaction.construction.blueprint.Blueprint.Companion.toStructure
import com.lambda.interaction.construction.simulation.BuildSimulator.simulate
import com.lambda.interaction.construction.simulation.context.BreakContext
import com.lambda.interaction.construction.simulation.result.results.BreakResult
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.handlers.breaking.BrokenBlockHandler.destroyBlock
import com.lambda.interaction.handlers.breaking.BrokenBlockHandler.pendingActions
import com.lambda.interaction.handlers.breaking.BrokenBlockHandler.setPendingConfigs
import com.lambda.interaction.handlers.breaking.BrokenBlockHandler.startPending
import com.lambda.interaction.handlers.breaking.RebreakHandler
import com.lambda.interaction.handlers.breaking.RebreakHandler.getRebreakPotential
import com.lambda.interaction.handlers.breaking.RebreakResult
import com.lambda.interaction.handlers.packet.PacketLimitHandler
import com.lambda.interaction.handlers.packet.PacketType
import com.lambda.interaction.managers.Manager
import com.lambda.interaction.managers.ManagerUtils.isPosBlocked
import com.lambda.interaction.managers.PositionBlocking
import com.lambda.interaction.managers.breaking.BreakInfo.BreakType.Primary
import com.lambda.interaction.managers.breaking.BreakInfo.BreakType.Rebreak
import com.lambda.interaction.managers.breaking.BreakInfo.BreakType.RedundantSecondary
import com.lambda.interaction.managers.breaking.BreakInfo.BreakType.Secondary
import com.lambda.interaction.managers.breaking.BreakManager.abandonedBreak
import com.lambda.interaction.managers.breaking.BreakManager.activeInfos
import com.lambda.interaction.managers.breaking.BreakManager.activeRequest
import com.lambda.interaction.managers.breaking.BreakManager.breakInfos
import com.lambda.interaction.managers.breaking.BreakManager.breaks
import com.lambda.interaction.managers.breaking.BreakManager.canAccept
import com.lambda.interaction.managers.breaking.BreakManager.checkForCancels
import com.lambda.interaction.managers.breaking.BreakManager.handlePreProcessing
import com.lambda.interaction.managers.breaking.BreakManager.hotbarRequest
import com.lambda.interaction.managers.breaking.BreakManager.initNewBreak
import com.lambda.interaction.managers.breaking.BreakManager.maxBreaksThisTick
import com.lambda.interaction.managers.breaking.BreakManager.nullify
import com.lambda.interaction.managers.breaking.BreakManager.populateFrom
import com.lambda.interaction.managers.breaking.BreakManager.processNewBreak
import com.lambda.interaction.managers.breaking.BreakManager.processRequest
import com.lambda.interaction.managers.breaking.BreakManager.rotationRequest
import com.lambda.interaction.managers.breaking.BreakManager.simulateAbandoned
import com.lambda.interaction.managers.breaking.BreakManager.updateBreakProgress
import com.lambda.interaction.managers.breaking.BreakManager.updatePreProcessing
import com.lambda.interaction.managers.breaking.SwapInfo.Companion.getSwapInfo
import com.lambda.interaction.managers.hotbar.HotbarRequest
import com.lambda.interaction.managers.interacting.InteractManager
import com.lambda.interaction.managers.rotating.RotationRequest
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.threading.runGameScheduled
import com.lambda.threading.runSafe
import com.lambda.threading.runSafeAutomated
import com.lambda.util.BlockUtils.calcItemBlockBreakingDelta
import com.lambda.util.BlockUtils.isEmpty
import com.lambda.util.BlockUtils.isNotBroken
import com.lambda.util.BlockUtils.isNotEmpty
import com.lambda.util.extension.tickDelta
import com.lambda.util.item.ItemUtils.block
import com.lambda.util.math.lerp
import com.lambda.util.player.PlayerUtils.gamemode
import com.lambda.util.player.PlayerUtils.swingHand
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.entity.ItemEntity
import net.minecraft.item.ItemStack
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import kotlin.math.max

/**
 * Manager responsible for breaking blocks in the most efficient manner possible. It can be accessed
 * from anywhere through a [BreakRequest].
 *
 * If configured with the right options enabled, this manager can break two blocks simultaneously, even if the two breaks come from
 * different requests. Each break will be handled using its own config, and just like the other managers, priority is a first-come, first-served
 * style system.
 */
object BreakManager : Manager<BreakRequest>(
	0,
	onOpen = {
		processRequest(activeRequest)
		simulateAbandoned()
			 },
	onClose = { checkForCancels() }
), PositionBlocking {
	private val breakInfos = arrayOfNulls<BreakInfo>(2)

	private val activeInfos
		get() = breakInfos
			.filterNotNull()
			.filter { it.type != RedundantSecondary }

	private var primaryBreak: BreakInfo?
		get() = breakInfos[0]
		set(value) { breakInfos[0] = value }

	private var secondaryBreak: BreakInfo?
		get() = breakInfos[1]
		set(value) { breakInfos[1] = value }

	private val abandonedBreak
		get() = breakInfos[1].let { secondary ->
			if (secondary?.abandoned == true && secondary.type != RedundantSecondary) secondary
			else null
		}

	val currentStackSelection
		get() = activeInfos
			.lastOrNull {
				it.breakConfig.doubleBreak || it.type == Secondary
			}?.context?.itemSelection
			?: StackSelection.EVERYTHING.select()

	override val blockedPositions
		get() = activeInfos.map { it.context.blockPos } + pendingActions.map { it.context.blockPos }

	private var activeRequest: BreakRequest? = null

	private var hotbarRequest: HotbarRequest? = null
	private val swapped get() = hotbarRequest?.done != false

	private var rotationRequest: RotationRequest? = null
	private val rotated get() = rotationRequest?.done != false

	private var breakDelay = 0
	var breaksThisTick = 0
	private var maxBreaksThisTick = 0

	private var breaks = mutableListOf<BreakContext>()

	var lastPosStarted: BlockPos? = null
		set(value) {
			if (value != field) RebreakHandler.clearRebreak()
			field = value
		}

	const val OLD_GRIM_Y_OFFSET = 955

	override fun load(): String {
		super.load()

		listen<TickEvent.Post>({ Int.MIN_VALUE }) {
			if (primaryBreak?.breaking == false) primaryBreak = null
			breakInfos.forEach { it?.tickChecks() }
			if (breakDelay > 0) breakDelay--
			activeRequest = null
			breaks = mutableListOf()
			breaksThisTick = 0
		}

		listen<WorldEvent.BlockUpdate.Server>({ Int.MIN_VALUE }) { event ->
			if (event.pos == RebreakHandler.reBreak?.context?.blockPos) return@listen

			breakInfos
				.firstOrNull { it?.context?.blockPos == event.pos }
				?.let { info ->
					// if not broken
					if (isNotBroken(info.context.cachedState, event.newState)) {
						// update the cached state
						info.context.cachedState = event.newState
						return@listen
					}
					destroyBlock(info)
					if (info.type == RedundantSecondary) {
						info.nullify()
						return@listen
					}
					info.request.onStop?.invoke(this@listen, info.context.blockPos)
					info.internalOnBreak()
					if (info.callbacksCompleted)
						RebreakHandler.offerRebreak(info)
					else info.startPending()
					info.nullify()
				}
		}

		// ToDo: Dependent on the tracked data order. When set stack is called after position it wont work
		listen<EntityEvent.Update>({ Int.MIN_VALUE }) {
			runGameScheduled {
				val entity = it.entity
				if (entity !is ItemEntity) return@runGameScheduled

				// ToDo: Proper item drop prediction system
				RebreakHandler.reBreak?.let { reBreak ->
					if (matchesBlockItem(reBreak, entity)) return@runGameScheduled
				}

				breakInfos
					.filterNotNull()
					.firstOrNull { info -> matchesBlockItem(info, entity) }
					?.internalOnItemDrop(entity)
			}
		}

		listenUnsafe<ConnectionEvent.Connect.Pre>({ Int.MIN_VALUE }) {
			primaryBreak = null
			secondaryBreak = null
			breakDelay = 0
		}

		immediateRenderer("BreakManager Immediate Renderer") {
			runSafe {
				val activeStack = breakInfos
					.filterNotNull()
					.firstOrNull()?.swapStack ?: return@immediateRenderer

				breakInfos
					.filterNotNull()
					.forEach { info ->
						if (!info.breaking) return@forEach

						val config = info.breakConfig
						if (!config.renders) return@immediateRenderer
						val swapMode = config.swapMode
						val breakDelta =
							info.request.runSafeAutomated {
								val useActiveStack = info.type != RedundantSecondary && swapMode.isEnabled() && swapMode != BreakConfig.SwapMode.Start
								info.calcBreakDelta(if (useActiveStack) activeStack else player.mainHandStack).toDouble()
							}
						val currentDelta = info.breakingTicks * breakDelta

						val threshold = if (info.type == Primary) config.breakThreshold else 1f
						val adjustedThreshold = threshold + (breakDelta * config.fudgeFactor)

						val currentProgress = currentDelta / adjustedThreshold
						val nextTicksProgress = (currentDelta + breakDelta) / adjustedThreshold
						val interpolatedProgress = lerp(mc.tickDelta, currentProgress, nextTicksProgress)

						val fillColor =
							if (config.dynamicFillColor)
								lerp(interpolatedProgress, config.startFillColor, config.endFillColor)
							else config.staticFillColor

						val outlineColor =
							if (config.dynamicOutlineColor)
								lerp(interpolatedProgress, config.startOutlineColor, config.endOutlineColor)
							else config.staticOutlineColor

						val pos = info.context.blockPos
						info.context.cachedState.getOutlineShape(world, pos).boundingBoxes.map {
							it.offset(pos)
						}.forEach { box ->
							val animationMode = config.animation
							val interpolatedBox = interpolateBox(box, interpolatedProgress, animationMode)
							box(interpolatedBox, config.outlineConfig) {
								if (!config.outline) hideOutline()
								if (!config.fill) hideFill()
								colors(fillColor, outlineColor)
							}
						}
					}
			}
		}

		return "Loaded Break Manager"
	}

	/**
	 * Attempts to accept and process the request, if there is not already an [activeRequest] and the
	 * [BreakRequest.contexts] collection is not empty. If nowOrNothing is true, the request is cleared
	 * after the first process.
	 *
	 * @see processRequest
	 */
	override fun AutomatedSafeContext.handleRequest(request: BreakRequest) {
		if (activeRequest != null || request.contexts.isEmpty()) return
		if (InteractManager.activeThisTick) return

		activeRequest = request
		processRequest(request)
		if (request.nowOrNothing) {
			activeRequest = null
			breaks = mutableListOf()
		}
	}

	/**
	 * Handles populating the manager, updating break progresses, and clearing the active request
	 * when all breaks are complete.
	 *
	 * @see populateFrom
	 * @see processNewBreak
	 * @see handlePreProcessing
	 * @see updateBreakProgress
	 */
	private fun SafeContext.processRequest(request: BreakRequest?) {
		request?.let { request ->
			if (request.fresh) populateFrom(request)
		}

		var noNew: Boolean
		var noProgression: Boolean

		while (true) {
			noNew = request?.let { !processNewBreak(request) } != false

			// Reversed so that the breaking order feels natural to the user as the primary break is always the
			// last break to be started
			handlePreProcessing()
			noProgression =
				activeInfos
					.filter { it.updatedThisTick && it.shouldProgress }
					.asReversed()
					.run {
						if (isEmpty()) true
						else {
							forEach { breakInfo ->
								updateBreakProgress(breakInfo)
							}
							false
						}
					}

			if (noNew && noProgression) break
		}

		if (breaks.isEmpty()) activeRequest = null
		if (breaksThisTick > 0 || activeInfos.isNotEmpty()) {
			activeThisTick = true
		}
	}

	/**
	 * Filters the requests [BreakContext]s, and iterates over the [breakInfos] collection looking for matches
	 * in positions. If a match is found, the [BreakInfo] is updated with the new context.
	 * The [breaks] collection is then populated with the new appropriate contexts, and the [maxBreaksThisTick]
	 * value is set.
	 *
	 * @see canAccept
	 * @see BreakInfo.updateInfo
	 */
	private fun SafeContext.populateFrom(request: BreakRequest) = request.runSafeAutomated {
		// Sanitize the new breaks
		val newBreaks = request.contexts
			.distinctBy { it.blockPos }
			.filter { canAccept(it) && (!request.nowOrNothing || it.instantBreak) }
			.toMutableList()

		// Update the current break infos
		breakInfos
			.filterNotNull()
			.forEach { info ->
				val ctx = newBreaks.find { ctx ->
					ctx.blockPos == info.context.blockPos
				} ?: return@forEach

				newBreaks.remove(ctx)

				if (info.updatedThisTick && info.type != RedundantSecondary && !info.abandoned) return@forEach

				when {
					info.type == RedundantSecondary -> info.request.onStart?.invoke(this, info.context.blockPos)
					info.abandoned -> {
						info.abandoned = false
						info.request.onStart?.invoke(this, info.context.blockPos)
					}
					else -> info.request.onUpdate?.invoke(this, info.context.blockPos)
				}

				info.updateInfo(ctx, request)
			}

		breaks = newBreaks
			.take((buildConfig.maxPendingActions - request.pendingInteractions.size).coerceAtLeast(0))
			.toMutableList()

		maxBreaksThisTick = breakConfig.breaksPerTick
	}

	/**
	 * @return if the break context can be accepted.
	 */
	private fun SafeContext.canAccept(newCtx: BreakContext): Boolean {
		if (activeInfos.none { it.context.blockPos == newCtx.blockPos } && isPosBlocked(newCtx.blockPos)) return false

		val hardness = newCtx.cachedState.getHardness(world, newCtx.blockPos)

		return newCtx.cachedState.isNotEmpty && (hardness != -1f || player.isCreative)
	}

	/**
	 * Updates the pre-processing for [BreakInfo] elements within [activeInfos] as long as they've been updated this tick.
	 * This method also populates [rotationRequest] and [hotbarRequest].
	 *
	 * @see updatePreProcessing
	 */
	private fun SafeContext.handlePreProcessing() {
		if (activeInfos.isEmpty()) return

		activeInfos
			.filter { it.updatedThisTick }
			.let { infos ->
				rotationRequest = infos.lastOrNull { info ->
					info.breakConfig.rotate
				}?.let { info ->
					val rotation = info.context.rotationRequest
					rotation.submit(false)
				}

				infos.forEach { it.updatePreProcessing() }

				val first = infos.firstOrNull() ?: return@let
				val last = infos.lastOrNull { it.swapInfo.swap && it.shouldProgress } ?: return@let

				val minKeepTicks = if (first.swapInfo.longSwap || last.swapInfo.longSwap) 1 else 0
				val serverSwapTicks = max(first.breakConfig.serverSwapTicks, last.breakConfig.serverSwapTicks)

				hotbarRequest = with(last) {
					HotbarRequest(
						context.hotbarIndex,
						request,
						request.hotbarConfig.keepTicks.coerceAtLeast(minKeepTicks),
						request.hotbarConfig.swapPause.coerceAtLeast(serverSwapTicks - 1)
					).submit(false)
				}

				return
			}

		hotbarRequest = null
	}

	/**
	 * Attempts to start breaking as many [BreakContext]'s from the [breaks] collection as possible.
	 *
	 * @return false if a context cannot be started or the maximum active breaks have been reached.
	 *
	 * @see initNewBreak
	 */
	private fun SafeContext.processNewBreak(request: BreakRequest): Boolean =
		request.runSafeAutomated {
			if (tickStage !in request.breakConfig.tickStageMask) return false
			if (breakDelay > 0) return false
			if (breaksThisTick >= maxBreaksThisTick) return false

			breaks.forEach { ctx ->
				if (!currentStackSelection.filterStack(player.inventory.getStack(ctx.hotbarIndex))) return@forEach

				initNewBreak(ctx, request) ?: return false
				breaks.remove(ctx)
				return true
			}
			return false
		}

	/**
	 * Attempts to accept the [requestCtx] into the [breakInfos].
	 *
	 * If a primary [BreakInfo] is active, as long as the tick stage is valid, it is transformed
	 * into a secondary break, so a new primary can be initialized. This means sending a
	 * PlayerActionC2SPacket with action: STOP_DESTROY_BLOCK
	 * packet to the server to start the automated breaking server side.
	 *
	 * If there is no way to keep both breaks, and the primary break hasn't been updated yet,
	 * the primary break is canceled. Otherwise, the break cannot be started.
	 *
	 * @return the [BreakInfo], or null, if the break context wasn't accepted.
	 *
	 * @see net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
	 */
	private fun AutomatedSafeContext.initNewBreak(
		requestCtx: BreakContext,
		request: BreakRequest
	): BreakInfo? {
		val breakInfo = BreakInfo(requestCtx, Primary, request)
		primaryBreak?.let { primary ->
			val primaryConfig = primary.breakConfig
			if (tickStage !in primaryConfig.tickStageMask) return null
			if (!PacketLimitHandler.canSendPackets(1, PacketType.PlayerAction)) return null

			if (!primaryConfig.doubleBreak || secondaryBreak != null) {
				if (!primary.updatedThisTick) {
					primary.cancelBreak()
					return@let
				} else return null
			}

			if (!primary.breaking) return null

			if (primaryConfig.breakMode == BreakMode.OldGrim) {
				val breakDelta = primary.calcBreakDelta()
				if (!primary.progressedThisTick) return null
				if (primary.breakingTicks * breakDelta >= primaryConfig.breakThreshold) return null
			}

			secondaryBreak =
				primary.apply {
					type = Secondary
					stopBreakPacket()
				}
			PacketLimitHandler.sentPackets(1, PacketType.PlayerAction)
			return@let
		}

		primaryBreak = breakInfo
		setPendingConfigs()
		return primaryBreak
	}

	/**
	 * Simulates and updates the [abandonedBreak].
	 */
	private fun SafeContext.simulateAbandoned() {
		// Canceled but double breaking so requires break manager to continue the simulation
		val abandonedInfo = abandonedBreak ?: return

		abandonedInfo.request.runSafeAutomated {
			abandonedInfo.context.blockPos
				.toStructure(TargetState.Empty)
				.simulate()
				.filterIsInstance<BreakResult.Break>()
				.filter { canAccept(it.context) }
				.sorted()
				.let { sim ->
					abandonedInfo.updateInfo(sim.firstOrNull()?.context ?: return)
				}
		}
	}

	/**
	 * Checks if any active [BreakInfo]s are not updated this tick, and are within the timeframe of a valid tick stage.
	 * If so, the [BreakInfo] is either canceled, or progressed if the break is redundant.
	 */
	private fun SafeContext.checkForCancels() {
		breakInfos
			.filterNotNull()
			.filter { !it.updatedThisTick && tickStage in it.breakConfig.tickStageMask }
			.forEach { info ->
				if (info.type == RedundantSecondary && !info.progressedThisTick) {
					val cachedState = info.context.cachedState
					if (cachedState.isEmpty) {
						info.nullify()
						return@forEach
					}
					info.request.runSafeAutomated {
						val breakDelta = info.calcBreakDelta()
						val ticksToBreak = 1.0 / breakDelta
						val ticksPast = info.breakingTicks - ticksToBreak
						if (ticksPast >= 200) {
							info.nullify()
							return@forEach
						}
					}
					info.progressedThisTick = true
					info.breakingTicks++
				} else info.cancelBreak()
			}
	}

	/**
	 * Begins the post-break logic sequence for the given [info].
	 *
	 * [BreakConfirmationMode.None] Will assume the block has been broken server side, and will only persist
	 * the [info] if the requester has any untriggered callbacks. E.g., if the block has broken, but the item hasn't dropped
	 * and the requester has specified an itemDrop callback.
	 *
	 * [BreakConfirmationMode.BreakThenAwait] Will perform all post-block break actions, such as spawning break particles,
	 * playing sounds, etc. However, it will store the [info] in the pending interaction collections before triggering the
	 * [BreakInfo.internalOnBreak] callback, in case the server rejects the break.
	 *
	 * [BreakConfirmationMode.AwaitThenBreak] Will immediately place the [info] into the pending interaction collections.
	 * Once the server responds, confirming the break, the post-break actions will take place, and the [BreakInfo.internalOnBreak]
	 * callback will be triggered.
	 *
	 * @see destroyBlock
	 * @see startPending
	 * @see nullify
	 */
	private fun AutomatedSafeContext.onBlockBreak(info: BreakInfo) {
		info.request.onStop?.invoke(this, info.context.blockPos)
		when (breakConfig.breakConfirmation) {
			BreakConfirmationMode.None -> {
				destroyBlock(info)
				info.internalOnBreak()
				if (!info.callbacksCompleted) {
					info.startPending()
				} else {
					RebreakHandler.offerRebreak(info)
				}
			}
			BreakConfirmationMode.BreakThenAwait -> {
				destroyBlock(info)
				info.startPending()
			}
			BreakConfirmationMode.AwaitThenBreak -> {
				info.startPending()
			}
		}
		breaksThisTick++
		if (info.type == Primary && !info.vanillaInstantBreakable) breakDelay = info.getBreakDelay()
		info.nullify()
	}

	context(_: SafeContext)
	private fun BreakInfo.updatePreProcessing() = request.runSafeAutomated {
		shouldProgress = !progressedThisTick
				&& tickStage in breakConfig.tickStageMask
				&& (rotated || type != Primary)

		if (updatedPreProcessingThisTick) return@runSafeAutomated
		updatedPreProcessingThisTick = true

		swapStack = player.inventory.getStack(context.hotbarIndex)
		rebreakPotential = getRebreakPotential()
		swapInfo = getSwapInfo()
	}

	/**
	 * Attempts to cancel the break.
	 *
	 * Secondary blocks are monitored by the server and keep breaking regardless of the clients' actions.
	 * This means that the break cannot be completely stopped. Instead, it must be monitored as we can't start
	 * another secondary [BreakInfo] until the previous has broken or its state has become empty.
	 *
	 * If the user has [BreakConfig.unsafeCancels] enabled, the info is made redundant, and mostly ignored.
	 * If not, the break continues.
	 */
	context(safeContext: SafeContext)
	private fun BreakInfo.cancelBreak() = with(safeContext) safeContext@{
		if (type == RedundantSecondary || abandoned) return@safeContext
		when (type) {
			Primary -> {
				with(request) {
					if (!PacketLimitHandler.canSendPackets(1, PacketType.PlayerAction)) return@safeContext
				}
				nullify()
				setBreakingTextureStage(-1)
				abortBreakPacket()
				PacketLimitHandler.sentPackets(1, PacketType.PlayerAction)
				request.onCancel?.invoke(this, context.blockPos)
			}
			Secondary -> {
				if (breakConfig.unsafeCancels) {
					type = RedundantSecondary
					setBreakingTextureStage(-1)
					request.onCancel?.invoke(this, context.blockPos)
				} else abandoned = true
			}
			else -> {}
		}
	}

	private fun BreakInfo.nullify() =
		when (type) {
			Primary, Rebreak -> primaryBreak = null
			else -> secondaryBreak = null
		}

	/**
	 * A modified version of the vanilla updateBlockBreakingProgress method.
	 *
	 * @return if the update was successful.
	 *
	 * @see net.minecraft.client.network.ClientPlayerInteractionManager.updateBlockBreakingProgress
	 */
	private fun SafeContext.updateBreakProgress(info: BreakInfo): Unit =
		info.request.runSafeAutomated {
			val ctx = info.context
			info.progressedThisTick = true

			if (!info.breaking) {
				if (info.swapInfo.swap && !swapped) return
				if (!startBreaking(info)) {
					info.nullify()
					info.request.onCancel?.invoke(this, ctx.blockPos)
				}
				return
			}

			val hitResult = ctx.hitResult

			if (gamemode.isCreative && world.worldBorder.contains(ctx.blockPos)) {
				if (!PacketLimitHandler.canSendPackets(1, PacketType.PlayerAction)) return
				lastPosStarted = ctx.blockPos
				onBlockBreak(info)
				info.startBreakPacket()
				PacketLimitHandler.sentPackets(1, PacketType.PlayerAction)
				if (breakConfig.swing.isEnabled()) swingHand(breakConfig.swingType, Hand.MAIN_HAND)
				return
			}

			val blockState = ctx.cachedState
			if (blockState.isEmpty) {
				info.nullify()
				info.request.onCancel?.invoke(this, ctx.blockPos)
				return
			}

			if (breakConfig.swapMode == BreakConfig.SwapMode.Constant && !swapped) return

			info.breakingTicks++

			val requiresDelayBypassing = info.type == Primary && breakConfig.breakMode == BreakMode.OldGrim && !info.bypassedDelay
			if (requiresDelayBypassing && PacketLimitHandler.canSendPackets(22, PacketType.PlayerAction) && info.breakingTicks > 6) {
				repeat(22) { info.startBreakPacket(OLD_GRIM_Y_OFFSET) }
				PacketLimitHandler.sentPackets(22, PacketType.PlayerAction)
				info.bypassedDelay = true
			}

			val breakDelta = info.calcBreakDelta()
			val progress = breakDelta * (info.breakingTicks - breakConfig.fudgeFactor)

			if (breakConfig.sounds) {
				if (info.soundsCooldown % 4.0f == 0.0f) {
					val blockSoundGroup = blockState.soundGroup
					mc.soundManager.play(
						PositionedSoundInstance(
							blockSoundGroup.hitSound,
							SoundCategory.BLOCKS,
							(blockSoundGroup.getVolume() + 1.0f) / 8.0f,
							blockSoundGroup.getPitch() * 0.5f,
							SoundInstance.createRandom(),
							ctx.blockPos
						)
					)
				}
				info.soundsCooldown++
			}

			if (breakConfig.particles) world.spawnBlockBreakingParticle(ctx.blockPos, hitResult.side)
			if (breakConfig.breakingTexture) info.setBreakingTextureStage()

			val swing = breakConfig.swing
			if (progress >= info.getBreakThreshold()) {
				if (info.swapInfo.swap && !swapped) return

				if (info.type == Primary) {
					if (breakConfig.breakMode == BreakMode.OldGrim && info.breakingTicks <= 6) return
					if (!PacketLimitHandler.canSendPackets(1, PacketType.PlayerAction)) return
				}

				onBlockBreak(info)
				if (info.type == Primary) {
					info.stopBreakPacket()
					PacketLimitHandler.sentPackets(1, PacketType.PlayerAction)
				}
				if (swing.isEnabled() && swing != BreakConfig.SwingMode.Start) swingHand(breakConfig.swingType, Hand.MAIN_HAND)
			} else {
				if (swing == BreakConfig.SwingMode.Constant) swingHand(breakConfig.swingType, Hand.MAIN_HAND)
			}
		}

	/**
	 * A modified version of the minecraft attackBlock method.
	 *
	 * @return if the block started breaking successfully.
	 *
	 * @see net.minecraft.client.network.ClientPlayerInteractionManager.attackBlock
	 */
	private fun AutomatedSafeContext.startBreaking(info: BreakInfo): Boolean {
		if (!PacketLimitHandler.canSendPackets(1, PacketType.PlayerAction)) return false
		val ctx = info.context

		if (info.rebreakPotential.isPossible()) {
			when (val rebreakResult = RebreakHandler.handleUpdate(info.context, info.request)) {
				is RebreakResult.StillBreaking -> {
					primaryBreak = rebreakResult.breakInfo.apply {
						type = Primary
						RebreakHandler.clearRebreak()
						request.onStart?.invoke(this@startBreaking, ctx.blockPos)
					}

					primaryBreak?.let { primary ->
						handlePreProcessing()
						updateBreakProgress(primary)
					}
					return true
				}
				is RebreakResult.Rebroke -> {
					info.type = Rebreak
					info.nullify()
					info.request.onReBreak?.invoke(this, ctx.blockPos)
					return true
				}
				else -> {}
			}
		}

		if (player.isBlockBreakingRestricted(world, ctx.blockPos, gamemode)) return false
		if (!world.worldBorder.contains(ctx.blockPos)) return false

		if (gamemode.isCreative) {
			lastPosStarted = ctx.blockPos
			info.request.onStart?.invoke(this, ctx.blockPos)
			onBlockBreak(info)
			info.startBreakPacket()
			PacketLimitHandler.sentPackets(1, PacketType.PlayerAction)
			if (breakConfig.swing.isEnabled()) swingHand(breakConfig.swingType, Hand.MAIN_HAND)
			return true
		}
		if (info.breaking) return false

		val progress = info.calcBreakDelta()
		val instantBreakable = progress >= info.getBreakThreshold()

		var packetCount = 1
		info.vanillaInstantBreakable = progress >= 1
		val isGrim = breakConfig.breakMode == BreakMode.Grim
		val oldGrim = breakConfig.breakMode == BreakMode.OldGrim && !info.vanillaInstantBreakable
		val requiresSecondStop = instantBreakable && !info.vanillaInstantBreakable

		if (isGrim) packetCount++
		else if (oldGrim) packetCount++
		if (requiresSecondStop) packetCount++

		if (!PacketLimitHandler.canSendPackets(packetCount, PacketType.PlayerAction)) return false

		info.request.onStart?.invoke(this, ctx.blockPos)

		lastPosStarted = ctx.blockPos

		if (info.breakingTicks == 0) {
			info.context.cachedState.onBlockBreakStart(world, ctx.blockPos, player)
		}

		if (instantBreakable) onBlockBreak(info)
		else {
			info.apply {
				breaking = true
				breakingTicks = 1
				soundsCooldown = 0.0f
				if (breakConfig.breakingTexture) setBreakingTextureStage()
			}
		}

		if (isGrim) info.stopBreakPacket()
		info.startBreakPacket()
		if (oldGrim) info.startBreakPacket(OLD_GRIM_Y_OFFSET)
		if (requiresSecondStop) info.stopBreakPacket()

		PacketLimitHandler.sentPackets(packetCount, PacketType.PlayerAction)

		if (breakConfig.swing.isEnabled() && (breakConfig.swing != BreakConfig.SwingMode.End || instantBreakable)) {
			swingHand(breakConfig.swingType, Hand.MAIN_HAND)
		}

		return true
	}

	/**
	 * Wrapper method for calculating block-breaking delta.
	 */
	context(automatedSafeContext: AutomatedSafeContext)
	fun BreakInfo.calcBreakDelta(
		item: ItemStack = automatedSafeContext.player.mainHandStack
	) = with(automatedSafeContext) {
		val delta = context.cachedState.calcItemBlockBreakingDelta(context.blockPos, item)
		//ToDo: This setting requires some fixes / improvements in the player movement prediction to work properly. Currently, it's broken
//        if (config.desyncFix) {
//            val nextTickPrediction = buildPlayerPrediction().next()
//            if (player.isOnGround && !nextTickPrediction.onGround) {
//                delta /= 5.0f
//            }
//
//            val affectedThisTick = player.isSubmergedIn(FluidTags.WATER) && !EnchantmentHelper.hasAquaAffinity(player)
//            val simulatedPlayer = nextTickPrediction.predictionEntity.player
//            val affectedNextTick = simulatedPlayer.isSubmergedIn(FluidTags.WATER) && !EnchantmentHelper.hasAquaAffinity(simulatedPlayer)
//            if (!affectedThisTick && affectedNextTick) {
//                delta /= 5.0f
//            }
//        }
		delta
	}

	/**
	 * @return if the [ItemEntity] matches the [BreakInfo]'s expected item drop.
	 */
	fun matchesBlockItem(info: BreakInfo, entity: ItemEntity): Boolean {
		val inRange = info.context.blockPos.toCenterPos().isInRange(entity.pos, 0.5)
		val correctMaterial = info.context.cachedState.block == entity.stack.item.block
		return inRange && correctMaterial
	}

	/**
	 * Interpolates the give [box] using the [BreakConfig]'s animation mode.
	 */
	private fun interpolateBox(box: Box, progress: Double, animationMode: BreakConfig.AnimationMode): Box {
		val boxCenter = Box(box.center, box.center)
		return when (animationMode) {
			BreakConfig.AnimationMode.Out -> lerp(progress, boxCenter, box)
			BreakConfig.AnimationMode.In -> lerp(progress, box, boxCenter)
			BreakConfig.AnimationMode.InOut ->
				if (progress >= 0.5f) lerp((progress - 0.5) * 2, boxCenter, box)
				else lerp(progress * 2, box, boxCenter)
			BreakConfig.AnimationMode.OutIn ->
				if (progress >= 0.5f) lerp((progress - 0.5) * 2, box, boxCenter)
				else lerp(progress * 2, boxCenter, box)
			else -> box
		}
	}
}
